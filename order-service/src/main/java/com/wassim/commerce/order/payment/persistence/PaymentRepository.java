package com.wassim.commerce.order.payment.persistence;

import com.wassim.commerce.order.payment.domain.Payment;
import com.wassim.commerce.order.payment.enums.EPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByOrderIdAndStatus(Long orderId, EPaymentStatus status);
    long countByOrderIdAndStatus(
            Long orderId,
            EPaymentStatus status
    );
}
