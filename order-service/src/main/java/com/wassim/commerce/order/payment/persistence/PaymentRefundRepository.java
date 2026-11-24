package com.wassim.commerce.order.payment.persistence;

import com.wassim.commerce.order.payment.domain.PaymentRefund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;

public interface PaymentRefundRepository extends JpaRepository<PaymentRefund, Long> {
    Optional<PaymentRefund> findByPayment_Id(Long paymentId);

@Modifying
@Query(value = """
        INSERT INTO payment_refunds (payment_id, amount, currency)
        VALUES (:paymentId, :amount, :currency)
        ON CONFLICT (payment_id) DO NOTHING
        """, nativeQuery = true)
int recordRefundRequired(
        @Param("paymentId") Long paymentId,
        @Param("amount") BigDecimal amount,
        @Param("currency") String currency
);
}
