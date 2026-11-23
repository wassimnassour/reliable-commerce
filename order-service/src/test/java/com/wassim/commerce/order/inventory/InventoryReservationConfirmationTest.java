package com.wassim.commerce.order.inventory;

import com.wassim.commerce.order.TestcontainersConfiguration;
import com.wassim.commerce.order.fixtures.CommerceFixtures;
import com.wassim.commerce.order.inventory.enums.EReservationStatus;
import com.wassim.commerce.order.inventory.persistence.InventoryRepository;
import com.wassim.commerce.order.inventory.persistence.InventoryReservationRepository;
import com.wassim.commerce.order.inventory.service.InventoryService;
import com.wassim.commerce.order.messaging.handlers.PaymentHandlers.PaymentCompletedEventHandler;
import com.wassim.commerce.order.messaging.outbox.events.PaymentCompletedEvent;
import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.enums.EOrderStatus;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.order.records.OrderRequest;
import com.wassim.commerce.order.order.service.OrderService;
import com.wassim.commerce.order.payment.dto.response.PaymentResponse;
import com.wassim.commerce.order.payment.enums.EPaymentStatus;
import com.wassim.commerce.order.payment.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.*;

@Import({TestcontainersConfiguration.class, CommerceFixtures.class})
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "inventory.expiration.poll-delay-ms=3600000"
})
class InventoryReservationConfirmationTest {
    @Autowired CommerceFixtures fixtures;
    @Autowired InventoryService inventoryService;
    @Autowired InventoryRepository inventory;
    @Autowired InventoryReservationRepository reservations;
    @Autowired OrderService orderService;
    @Autowired OrderRepository orders;
    @Autowired PaymentService paymentService;
    @Autowired PaymentCompletedEventHandler handler;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate transactions;

    @Test
    void successfulPaymentWaitsForEventThenConfirmsAllProductsExactlyOnce() {
        var order = createOrder();
        var payment = paymentService.initiatePayment(order.id(), order.userId());
        paymentService.completePayment(payment.paymentId(), EPaymentStatus.SUCCEEDED);
        assertThat(orders.findById(order.id()).orElseThrow().getOrderStatus()).isEqualTo(EOrderStatus.PAID);
        assertState(order, EReservationStatus.RESERVED, 100, 2, 100, 3);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM outbox_messages WHERE aggregate_type = 'Payments' AND aggregate_id = ?",
                Long.class, payment.paymentId().toString())).isEqualTo(1);

        String messageId = UUID.randomUUID().toString();
        deliver(payment, EPaymentStatus.SUCCEEDED, messageId);
        assertState(order, EReservationStatus.CONFIRMED, 98, 0, 97, 0);
        var versions = versions(order);
        deliver(payment, EPaymentStatus.SUCCEEDED, messageId);
        deliver(payment, EPaymentStatus.SUCCEEDED, UUID.randomUUID().toString());
        assertState(order, EReservationStatus.CONFIRMED, 98, 0, 97, 0);
        assertThat(versions(order)).isEqualTo(versions);
    }

    @Test
    void failedEventLeavesInventoryReservedAndAllowsPaymentRetry() {
        var order = createOrder();
        var failed = paymentService.initiatePayment(order.id(), order.userId());
        paymentService.completePayment(failed.paymentId(), EPaymentStatus.FAILED);
        deliver(failed, EPaymentStatus.FAILED, UUID.randomUUID().toString());
        assertState(order, EReservationStatus.RESERVED, 100, 2, 100, 3);
        var retry = paymentService.initiatePayment(order.id(), order.userId());
        assertThat(retry.paymentId()).isNotEqualTo(failed.paymentId());
        paymentService.completePayment(retry.paymentId(), EPaymentStatus.SUCCEEDED);
        deliver(retry, EPaymentStatus.SUCCEEDED, UUID.randomUUID().toString());
        assertState(order, EReservationStatus.CONFIRMED, 98, 0, 97, 0);
    }

    @Test
    void confirmedFirstProductDoesNotPreventConfirmingRemainingProducts() {
        var order = createOrder();
        transactions.executeWithoutResult(status -> {
            var first = reservations.findAllByOrderIdForUpdate(order.id()).get(0);
            assertThat(inventory.confirmStock(order.productIds().get(0), 2)).isEqualTo(1);
            first.setStatus(EReservationStatus.CONFIRMED);
        });
        var firstVersion = versions(order).get(0);
        inventoryService.confirm(order.id());
        assertState(order, EReservationStatus.CONFIRMED, 98, 0, 97, 0);
        assertThat(versions(order).get(0)).isEqualTo(firstVersion);
    }

    @Test
    void cannotConfirmExpiredReservation() {
        rejectsInactiveReservation(EReservationStatus.EXPIRED);
    }

    @Test
    void cannotConfirmReleasedReservation() {
        rejectsInactiveReservation(EReservationStatus.RELEASED);
    }

    private void rejectsInactiveReservation(EReservationStatus inactive) {
        var order = createOrder();
        // Fail on the second product to verify the first update is rolled back.
        jdbc.update("UPDATE inventory_reservations SET status = ? WHERE order_id = ? AND product_id = ?",
                inactive.name(), order.id(), order.productIds().get(1));
        var before = versions(order);
        assertThatThrownBy(() -> inventoryService.confirm(order.id()))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("released or expired");
        var rows = reservations.findAllByOrderId(order.id());
        assertThat(rows.get(0).getStatus()).isEqualTo(EReservationStatus.RESERVED);
        assertThat(rows.get(1).getStatus()).isEqualTo(inactive);
        assertStock(order, 100, 2, 100, 3);
        assertThat(versions(order)).isEqualTo(before);
    }

    @Test
    void failedSecondStockUpdateRollsBackFirstProductAndInboxClaimThenCanRetry() {
        var order = createOrder();
        var payment = paymentService.initiatePayment(order.id(), order.userId());
        paymentService.completePayment(payment.paymentId(), EPaymentStatus.SUCCEEDED);
        jdbc.update("UPDATE inventory SET reserved_quantity = 0 WHERE product_id = ?", order.productIds().get(1));
        var before = versions(order);
        String messageId = UUID.randomUUID().toString();
        assertThatThrownBy(() -> deliver(payment, EPaymentStatus.SUCCEEDED, messageId))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Cannot confirm reserved stock");
        assertState(order, EReservationStatus.RESERVED, 100, 2, 100, 0);
        assertThat(versions(order)).isEqualTo(before);
        jdbc.update("UPDATE inventory SET reserved_quantity = 3 WHERE product_id = ?", order.productIds().get(1));
        deliver(payment, EPaymentStatus.SUCCEEDED, messageId);
        assertState(order, EReservationStatus.CONFIRMED, 98, 0, 97, 0);
    }

    @Test
    void concurrentConfirmationsWaitForReservationLocksAndConsumeOnlyOnce() throws Exception {
        var order = createOrder();
        var executor = Executors.newSingleThreadExecutor();
        try {
            var concurrent = transactions.execute(status -> {
                // Hold the reservation locks in the first transaction.
                reservations.findAllByOrderIdForUpdate(order.id());
                var started = new CountDownLatch(1);
                var other = executor.submit(() -> {
                    started.countDown();
                    inventoryService.confirm(order.id());
                });
                try {
                    assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThatThrownBy(() -> other.get(300, TimeUnit.MILLISECONDS)).isInstanceOf(TimeoutException.class);
                    inventoryService.confirm(order.id());
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
                return other;
            });
            concurrent.get(10, TimeUnit.SECONDS);
            executor.shutdown();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            assertState(order, EReservationStatus.CONFIRMED, 98, 0, 97, 0);
            // Reserve increments once and confirmation increments once per product.
            assertThat(versions(order)).containsExactly(2L, 2L);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void guardedStockUpdateRejectsInvalidQuantitiesAndIncrementsVersionOnSuccess() {
        var order = createOrder();
        var productId = order.productIds().get(0);
        var before = versions(order);
        transactions.executeWithoutResult(status -> {
            assertThat(inventory.confirmStock(productId, 0)).isZero();
            assertThat(inventory.confirmStock(productId, -1)).isZero();
            assertThat(inventory.confirmStock(productId, 3)).isZero();
        });
        assertStock(order, 100, 2, 100, 3);
        assertThat(versions(order)).isEqualTo(before);
        inventoryService.confirm(order.id());
        assertThat(versions(order)).containsExactly(before.get(0) + 1, before.get(1) + 1);
    }

    private TestOrder createOrder() {
        var user = fixtures.createUser();
        var first = fixtures.createProduct();
        var second = fixtures.createProduct();
        var order = orderService.createOrder(UUID.randomUUID().toString(), user.getId(),
                new CreateRequestDto(List.of(new OrderRequest(first.getId(), 2, "Test Street"),
                        new OrderRequest(second.getId(), 3, "Test Street"))));
        return new TestOrder(order.orderId(), user.getId(), List.of(first.getId(), second.getId()));
    }

    private void deliver(PaymentResponse payment, EPaymentStatus status, String messageId) {
        handler.handle(mapper.writeValueAsString(new PaymentCompletedEvent(payment.paymentId(), payment.orderId(), status)), messageId);
    }

    private List<Long> versions(TestOrder order) {
        return order.productIds().stream().map(id -> inventory.findByProductId(id).orElseThrow().getVersion()).toList();
    }

    private void assertState(TestOrder order, EReservationStatus status, int firstTotal, int firstReserved, int secondTotal, int secondReserved) {
        assertThat(reservations.findAllByOrderId(order.id())).hasSize(2)
                .allSatisfy(row -> assertThat(row.getStatus()).isEqualTo(status));
        assertStock(order, firstTotal, firstReserved, secondTotal, secondReserved);
    }

    private void assertStock(TestOrder order, int firstTotal, int firstReserved, int secondTotal, int secondReserved) {
        var first = inventory.findByProductId(order.productIds().get(0)).orElseThrow();
        var second = inventory.findByProductId(order.productIds().get(1)).orElseThrow();
        assertThat(first.getTotalQuantity()).isEqualTo(firstTotal);
        assertThat(first.getReservedQuantity()).isEqualTo(firstReserved);
        assertThat(second.getTotalQuantity()).isEqualTo(secondTotal);
        assertThat(second.getReservedQuantity()).isEqualTo(secondReserved);
    }

    private record TestOrder(Long id, Long userId, List<Long> productIds) {}
}
