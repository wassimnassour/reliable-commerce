
package com.wassim.commerce.order.payment.service;

import com.wassim.commerce.order.TestcontainersConfiguration;
import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.exaptions.IdempotencyConflictException;
import com.wassim.commerce.order.fixtures.CommerceFixtures;
import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.order.enums.EOrderStatus;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.order.service.OrderService;
import com.wassim.commerce.order.payment.api.PaymentController;
import com.wassim.commerce.order.payment.enums.EPaymentStatus;
import com.wassim.commerce.order.payment.persistence.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Import({TestcontainersConfiguration.class, CommerceFixtures.class})
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class PaymentServiceTest {

    @Autowired
    CommerceFixtures fixtures;

    @Autowired
    PaymentService paymentService;

    @Autowired
    PaymentController paymentController;

    @Autowired
    PaymentRepository payments;

    @Autowired
    OrderRepository orders;

    @Autowired
    OrderService orderService;

    @Autowired
    TransactionTemplate transactions;

    @Test
    void initiatesPaymentForPersistedOrder() {
        var user = fixtures.createUser();
        var product = fixtures.createProduct();
        var order = fixtures.createOrder(
                user, product, EOrderStatus.PENDING_PAYMENT
        );

        var response = paymentService.initiatePayment(
                order.getId(), user.getId()
        );

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.amount())
                .isEqualByComparingTo(order.getTotalPrice());
        assertThat(response.status()).isEqualTo(EPaymentStatus.PENDING);

        var payment = payments.findById(
                response.paymentId()
        ).orElseThrow();

        assertThat(payment.getCurrency())
                .isEqualTo(order.getCurrency());
    }

    @Test
    void paymentKeyReplaysTheOriginalResponseAndRejectsADifferentOrder() {
        var user = fixtures.createUser();
        var product = fixtures.createProduct();

        var order = fixtures.createOrder(
                user, product, EOrderStatus.PENDING_PAYMENT
        );

        var otherOrder = fixtures.createOrder(
                user, product, EOrderStatus.PENDING_PAYMENT
        );

        var jwt = Jwt.withTokenValue("test")
                .header("alg", "HS256")
                .subject(user.getId().toString())
                .build();

        var original = paymentController.initiatePayment(
                order.getId(), jwt, "payment-retry"
        );

        paymentService.completePayment(
                original.paymentId(),
                EPaymentStatus.SUCCEEDED
        );

        var replay = paymentController.initiatePayment(
                order.getId(), jwt, "payment-retry"
        );

        assertThat(replay.paymentId())
                .isEqualTo(original.paymentId());

        assertThat(replay.status())
                .isEqualTo(EPaymentStatus.PENDING);

        assertThatThrownBy(() ->
                paymentController.initiatePayment(
                        otherOrder.getId(), jwt, "payment-retry"
                )
        ).isInstanceOf(IdempotencyConflictException.class);
    }

    @Test
    void testCompletePaymentWithPendingPaymentAndPendingOrder() {
        var results = generateOrderPayment();

        paymentService.completePayment(
                results.paymentId(),
                EPaymentStatus.SUCCEEDED
        );

        var paymentAfterComplete = payments.findById(
                results.paymentId()
        ).orElseThrow();

        var orderAfterComplete = orders.findById(
                results.order().getId()
        ).orElseThrow();

        assertEquals(
                EPaymentStatus.SUCCEEDED,
                paymentAfterComplete.getStatus()
        );

        assertEquals(
                EOrderStatus.PAID,
                orderAfterComplete.getOrderStatus()
        );
    }

    @Test
    void testCompletePaymentWithFailedResult() {
        var results = generateOrderPayment();

        paymentService.completePayment(
                results.paymentId(),
                EPaymentStatus.FAILED
        );

        var paymentAfterComplete = payments.findById(
                results.paymentId()
        ).orElseThrow();

        var orderAfterComplete = orders.findById(
                results.order().getId()
        ).orElseThrow();

        assertEquals(
                EPaymentStatus.FAILED,
                paymentAfterComplete.getStatus()
        );

        assertEquals(
                EOrderStatus.PENDING_PAYMENT,
                orderAfterComplete.getOrderStatus()
        );
    }

    @Test
    void testCompletePaymentWithDuplicateSucceededResult() {
        var results = generateOrderPayment();

        // First webhook
        paymentService.completePayment(
                results.paymentId(),
                EPaymentStatus.SUCCEEDED
        );

        // Duplicate webhook
        paymentService.completePayment(
                results.paymentId(),
                EPaymentStatus.SUCCEEDED
        );

        var paymentAfterComplete = payments.findById(
                results.paymentId()
        ).orElseThrow();

        var orderAfterComplete = orders.findById(
                results.order().getId()
        ).orElseThrow();

        assertEquals(
                EPaymentStatus.SUCCEEDED,
                paymentAfterComplete.getStatus()
        );

        assertEquals(
                EOrderStatus.PAID,
                orderAfterComplete.getOrderStatus()
        );
    }

    @Test
    void testCompletePaymentWithConflictingResult() {
        var results = generateOrderPayment();

        // Initial result: FAILED
        paymentService.completePayment(
                results.paymentId(),
                EPaymentStatus.FAILED
        );

        // Conflicting result: SUCCEEDED
        BadRequest exception = assertThrows(
                BadRequest.class,
                () -> paymentService.completePayment(
                        results.paymentId(),
                        EPaymentStatus.SUCCEEDED
                )
        );

        assertEquals(
                "Payment already completed",
                exception.getMessage()
        );

        var paymentAfterComplete = payments.findById(
                results.paymentId()
        ).orElseThrow();

        var orderAfterComplete = orders.findById(
                results.order().getId()
        ).orElseThrow();

        assertEquals(
                EPaymentStatus.FAILED,
                paymentAfterComplete.getStatus()
        );

        assertEquals(
                EOrderStatus.PENDING_PAYMENT,
                orderAfterComplete.getOrderStatus()
        );
    }

    @Test
    void testCompletePaymentWithInvalidResult() {
        var results = generateOrderPayment();

        BadRequest exception = assertThrows(
                BadRequest.class,
                () -> paymentService.completePayment(
                        results.paymentId(),
                        EPaymentStatus.PENDING
                )
        );

        assertEquals(
                "Invalid payment result",
                exception.getMessage()
        );

        var paymentAfterComplete = payments.findById(
                results.paymentId()
        ).orElseThrow();

        assertEquals(
                EPaymentStatus.PENDING,
                paymentAfterComplete.getStatus()
        );
    }

    @Test
    void cancellationWaitsForPaymentCompletionAndRejectsThePaidOrder() throws Exception {
        var user = fixtures.createUser();
        var product = fixtures.createProduct();
        var order = fixtures.createOrder(user, product, EOrderStatus.PENDING_PAYMENT);
        var payment = paymentService.initiatePayment(order.getId(), user.getId());
        var completionReady = new CountDownLatch(1);
        var commitCompletion = new CountDownLatch(1);
        var cancellationStarted = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);

        try {
            var completion = executor.submit(() -> transactions.executeWithoutResult(status -> {
                paymentService.completePayment(payment.paymentId(), EPaymentStatus.SUCCEEDED);
                completionReady.countDown();
                try {
                    assertThat(commitCompletion.await(10, TimeUnit.SECONDS)).isTrue();
                } catch (InterruptedException exception) {
                    Thread.currentThread().interrupt();
                    throw new IllegalStateException(exception);
                }
            }));
            assertThat(completionReady.await(5, TimeUnit.SECONDS)).isTrue();

            var cancellation = executor.submit(() -> {
                cancellationStarted.countDown();
                return assertThrows(BadRequest.class, () -> orderService.cancelOrder(user.getId(), order.getId()));
            });
            assertThat(cancellationStarted.await(5, TimeUnit.SECONDS)).isTrue();
            assertThrows(TimeoutException.class, () -> cancellation.get(300, TimeUnit.MILLISECONDS));

            commitCompletion.countDown();
            completion.get(5, TimeUnit.SECONDS);
            assertThat(cancellation.get(5, TimeUnit.SECONDS)).hasMessage("Only unpaid orders can be canceled");
            assertThat(orders.findById(order.getId()).orElseThrow().getOrderStatus()).isEqualTo(EOrderStatus.PAID);
            assertThat(payments.findById(payment.paymentId()).orElseThrow().getStatus())
                    .isEqualTo(EPaymentStatus.SUCCEEDED);
        } finally {
            commitCompletion.countDown();
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    private OrderPaymentFixture generateOrderPayment() {
        var user = fixtures.createUser();
        var product = fixtures.createProduct();

        var order = fixtures.createOrder(
                user, product, EOrderStatus.PENDING_PAYMENT
        );

        var payment = paymentService.initiatePayment(
                order.getId(), user.getId()
        );

        return new OrderPaymentFixture(
                order,
                payment.paymentId()
        );
    }

    private record OrderPaymentFixture(
            Order order,
            Long paymentId
    ) {}
}
