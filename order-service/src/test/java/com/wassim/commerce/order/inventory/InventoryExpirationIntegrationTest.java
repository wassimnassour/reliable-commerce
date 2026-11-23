package com.wassim.commerce.order.inventory;

import com.wassim.commerce.order.TestcontainersConfiguration;
import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.fixtures.CommerceFixtures;
import com.wassim.commerce.order.inventory.enums.EReservationStatus;
import com.wassim.commerce.order.inventory.persistence.InventoryRepository;
import com.wassim.commerce.order.inventory.persistence.InventoryReservationRepository;
import com.wassim.commerce.order.inventory.service.impl.InventoryExpirationService;
import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.enums.EOrderStatus;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.order.records.OrderRequest;
import com.wassim.commerce.order.order.service.OrderService;
import com.wassim.commerce.order.payment.enums.EPaymentStatus;
import com.wassim.commerce.order.payment.persistence.PaymentRepository;
import com.wassim.commerce.order.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

// Service calls commit independently, so assertions observe real transaction boundaries.
@Import({TestcontainersConfiguration.class, CommerceFixtures.class})
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "inventory.expiration.poll-delay-ms=3600000"
})
class InventoryExpirationIntegrationTest {
    @Autowired CommerceFixtures fixtures;
    @Autowired OrderService orderService;
    @Autowired PaymentService paymentService;
    @Autowired InventoryExpirationService expiration;
    @Autowired InventoryReservationRepository reservations;
    @Autowired InventoryRepository inventory;
    @Autowired OrderRepository orders;
    @Autowired PaymentRepository payments;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void expiresBothUnpaidReservationsAndReleasesInventoryExactlyOnce() {
        var order = createOrder();
        assertReservations(order, EReservationStatus.RESERVED);
        assertStock(order, 2, 3);
        passDeadline(order);

        assertThat(expiration.processBatch()).isEqualTo(1);
        assertExpired(order);
        assertThat(expiration.processBatch()).isZero();
        assertExpired(order);
    }

    @Test
    void rejectsLateSuccessWithoutMutatingPaymentOrInventoryThenExpires() {
        var order = createOrder();
        var payment = paymentService.initiatePayment(order.id(), order.userId());
        passDeadline(order);
        long paymentCount = payments.count();
        long reservationCount = reservations.count();
        long outboxCount = jdbc.queryForObject("SELECT count(*) FROM outbox_messages", Long.class);

        assertThatThrownBy(() -> paymentService.completePayment(payment.paymentId(), EPaymentStatus.SUCCEEDED))
                .isInstanceOf(BadRequest.class).hasMessage("Inventory reservation has expired");
        assertThat(payments.findById(payment.paymentId()).orElseThrow().getStatus()).isEqualTo(EPaymentStatus.PENDING);
        assertThat(orders.findById(order.id()).orElseThrow().getOrderStatus()).isEqualTo(EOrderStatus.PENDING_PAYMENT);
        assertReservations(order, EReservationStatus.RESERVED);
        assertStock(order, 2, 3);
        assertThat(payments.count()).isEqualTo(paymentCount);
        assertThat(reservations.count()).isEqualTo(reservationCount);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages", Long.class)).isEqualTo(outboxCount);

        expiration.processBatch();
        assertExpired(order);
    }

    @Test
    void protectsPaidOrderAndPreservesSuccessfulCallbackIdempotencyAfterDeadline() {
        var order = createOrder();
        var payment = paymentService.initiatePayment(order.id(), order.userId());
        paymentService.completePayment(payment.paymentId(), EPaymentStatus.SUCCEEDED);
        passDeadline(order);
        paymentService.completePayment(payment.paymentId(), EPaymentStatus.SUCCEEDED);

        assertThat(expiration.processBatch()).isZero();
        assertThat(orders.findById(order.id()).orElseThrow().getOrderStatus()).isEqualTo(EOrderStatus.PAID);
        assertThat(payments.findById(payment.paymentId()).orElseThrow().getStatus()).isEqualTo(EPaymentStatus.SUCCEEDED);
        assertReservations(order, EReservationStatus.RESERVED);
        assertStock(order, 2, 3);
    }

    @Test
    void expiresWithFailedAndPendingPaymentsAndStillAcceptsLateFailure() {
        var order = createOrder();
        var failed = paymentService.initiatePayment(order.id(), order.userId());
        passDeadline(order);
        paymentService.completePayment(failed.paymentId(), EPaymentStatus.FAILED);
        var pending = paymentService.initiatePayment(order.id(), order.userId());

        assertThat(expiration.processBatch()).isEqualTo(1);
        assertExpired(order);
        assertThat(payments.findById(failed.paymentId()).orElseThrow().getStatus()).isEqualTo(EPaymentStatus.FAILED);
        assertThat(payments.findById(pending.paymentId()).orElseThrow().getStatus()).isEqualTo(EPaymentStatus.PENDING);
    }

    @Test
    void skipsInconsistentPendingOrderWithAnySuccessfulPayment() {
        var order = createOrder();
        var failed = paymentService.initiatePayment(order.id(), order.userId());
        paymentService.completePayment(failed.paymentId(), EPaymentStatus.FAILED);
        var successful = paymentService.initiatePayment(order.id(), order.userId());
        paymentService.completePayment(successful.paymentId(), EPaymentStatus.SUCCEEDED);
        // Simulate historical inconsistent state with multiple payment records.
        jdbc.update("UPDATE orders SET order_status = 'PENDING_PAYMENT' WHERE id = ?", order.id());
        passDeadline(order);

        assertThat(expiration.processBatch()).isZero();
        assertThat(orders.findById(order.id()).orElseThrow().getOrderStatus()).isEqualTo(EOrderStatus.PENDING_PAYMENT);
        assertStock(order, 2, 3);
        assertReservations(order, EReservationStatus.RESERVED);
        assertThat(payments.findById(successful.paymentId()).orElseThrow().getStatus()).isEqualTo(EPaymentStatus.SUCCEEDED);
        // Keep the deliberately inconsistent row out of subsequent batch assertions.
        jdbc.update("UPDATE orders SET order_status = 'PAID' WHERE id = ?", order.id());
    }

    @Test
    void skipsOrderLockedByAnotherTransactionThenExpiresAfterUnlock() throws Exception {
        var order = createOrder();
        passDeadline(order);
        var executor = Executors.newSingleThreadExecutor();
        try {
            transactions.executeWithoutResult(status -> {
                orders.findAndLockById(order.id()).orElseThrow();
                var batch = executor.submit(() -> expiration.processBatch());
                try {
                    assertThat(batch.get(5, TimeUnit.SECONDS)).isZero();
                } catch (Exception exception) {
                    throw new AssertionError("Expiration should skip a locked Order", exception);
                }
                assertStock(order, 2, 3);
            });
            assertThat(expiration.processBatch()).isEqualTo(1);
            assertExpired(order);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void rollsBackAllReleasesAndStatusesIfAnyInventoryReleaseFails() {
        var order = createOrder();
        passDeadline(order);
        // The second release fails after the first has executed in the same batch.
        jdbc.update("UPDATE inventory SET reserved_quantity = 0 WHERE product_id = ?", order.productIds().get(1));
        assertThatThrownBy(() -> expiration.processBatch()).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Failed to release reserved inventory");
        assertThat(orders.findById(order.id()).orElseThrow().getOrderStatus()).isEqualTo(EOrderStatus.PENDING_PAYMENT);
        assertReservations(order, EReservationStatus.RESERVED);
        assertStock(order, 2, 0);
        jdbc.update("UPDATE inventory SET reserved_quantity = 3 WHERE product_id = ?", order.productIds().get(1));
        assertThat(expiration.processBatch()).isEqualTo(1);
        assertExpired(order);
    }

    private TestOrder createOrder() {
        var user = fixtures.createUser();
        var first = fixtures.createProduct();
        var second = fixtures.createProduct();
        var response = orderService.createOrder(UUID.randomUUID().toString(), user.getId(),
                new CreateRequestDto(List.of(new OrderRequest(first.getId(), 2, "Test Street"),
                        new OrderRequest(second.getId(), 3, "Test Street"))));
        return new TestOrder(response.orderId(), user.getId(), List.of(first.getId(), second.getId()));
    }

    private void passDeadline(TestOrder order) {
        jdbc.update("UPDATE inventory_reservations SET expires_at = CURRENT_TIMESTAMP - INTERVAL '1 minute' WHERE order_id = ?", order.id());
    }

    private void assertReservations(TestOrder order, EReservationStatus expected) {
        assertThat(reservations.findAllByOrderId(order.id())).hasSize(2)
                .allSatisfy(reservation -> assertThat(reservation.getStatus()).isEqualTo(expected));
    }

    private void assertStock(TestOrder order, int firstReserved, int secondReserved) {
        for (int i = 0; i < order.productIds().size(); i++) {
            var stock = inventory.findByProductId(order.productIds().get(i)).orElseThrow();
            assertThat(stock.getTotalQuantity()).isEqualTo(100);
            assertThat(stock.getReservedQuantity()).isEqualTo(i == 0 ? firstReserved : secondReserved);
        }
    }

    private void assertExpired(TestOrder order) {
        assertThat(orders.findById(order.id()).orElseThrow().getOrderStatus()).isEqualTo(EOrderStatus.EXPIRED);
        assertReservations(order, EReservationStatus.EXPIRED);
        assertStock(order, 0, 0);
    }

    private record TestOrder(Long id, Long userId, List<Long> productIds) {}
}
