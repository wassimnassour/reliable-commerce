package com.wassim.commerce.order.payment.service;

import com.wassim.commerce.order.TestcontainersConfiguration;
import com.wassim.commerce.order.exaptions.IdempotencyConflictException;
import com.wassim.commerce.order.fixtures.CommerceFixtures;
import com.wassim.commerce.order.order.enums.EOrderStatus;
import com.wassim.commerce.order.payment.api.PaymentController;
import com.wassim.commerce.order.payment.enums.EPaymentStatus;
import com.wassim.commerce.order.payment.persistence.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import({TestcontainersConfiguration.class, CommerceFixtures.class})
@SpringBootTest(properties = "spring.rabbitmq.listener.simple.auto-startup=false")
class PaymentServiceTest {
    @Autowired CommerceFixtures fixtures;
    @Autowired PaymentService paymentService;
    @Autowired PaymentController paymentController;
    @Autowired PaymentRepository payments;

    @Test
    void initiatesPaymentForPersistedOrder() {
        var user = fixtures.createUser();
        var product = fixtures.createProduct();
        var order = fixtures.createOrder(user, product, EOrderStatus.PENDING_PAYMENT);

        var response = paymentService.initiatePayment(order.getId(), user.getId());

        assertThat(response.orderId()).isEqualTo(order.getId());
        assertThat(response.amount()).isEqualByComparingTo(order.getTotalPrice());
        assertThat(response.status()).isEqualTo(EPaymentStatus.PENDING);
        var payment = payments.findById(response.paymentId()).orElseThrow();
        assertThat(payment.getCurrency()).isEqualTo(order.getCurrency());
    }

    @Test
    void paymentKeyReplaysTheOriginalResponseAndRejectsADifferentOrder() {
        var user = fixtures.createUser();
        var product = fixtures.createProduct();
        var order = fixtures.createOrder(user, product, EOrderStatus.PENDING_PAYMENT);
        var otherOrder = fixtures.createOrder(user, product, EOrderStatus.PENDING_PAYMENT);
        var jwt = Jwt.withTokenValue("test").header("alg", "HS256").subject(user.getId().toString()).build();

        var original = paymentController.initiatePayment(order.getId(), jwt, "payment-retry");
        var payment = payments.findById(original.paymentId()).orElseThrow();
        payment.setStatus(EPaymentStatus.SUCCEEDED);
        payments.saveAndFlush(payment);

        var replay = paymentController.initiatePayment(order.getId(), jwt, "payment-retry");
        assertThat(replay.paymentId()).isEqualTo(original.paymentId());
        assertThat(replay.status()).isEqualTo(EPaymentStatus.PENDING);
        assertThatThrownBy(() -> paymentController.initiatePayment(otherOrder.getId(), jwt, "payment-retry"))
                .isInstanceOf(IdempotencyConflictException.class);
    }
}
