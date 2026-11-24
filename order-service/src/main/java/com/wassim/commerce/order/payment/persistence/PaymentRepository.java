package com.wassim.commerce.order.payment.persistence;

import com.wassim.commerce.order.payment.domain.Payment;
import com.wassim.commerce.order.payment.enums.EPaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<Payment, Long> {
    interface ReconciliationCandidate {
        Long getId();
        Long getOrderId();
    }

    @Query(value = """
            SELECT id, order_id AS "orderId" FROM payments
            WHERE status = 'PENDING'
              AND created_at < CURRENT_TIMESTAMP - (:minimumAgeMs * INTERVAL '1 millisecond')
              AND reconciliation_next_attempt_at <= CURRENT_TIMESTAMP
              AND (reconciliation_claim_until IS NULL OR reconciliation_claim_until <= CURRENT_TIMESTAMP)
            ORDER BY reconciliation_next_attempt_at, id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<ReconciliationCandidate> findToReconcile(@Param("minimumAgeMs") long minimumAgeMs,
                                               @Param("batchSize") int batchSize);

    @Modifying
    @Query(value = """
            UPDATE payments SET reconciliation_claim_token = :token,
                reconciliation_claim_until = CURRENT_TIMESTAMP + (:leaseMs * INTERVAL '1 millisecond'),
                reconciliation_next_attempt_at = CURRENT_TIMESTAMP + (:leaseMs * INTERVAL '1 millisecond')
            WHERE id IN (:ids)
            """, nativeQuery = true)
    int claimReconciliation(@Param("ids") List<Long> ids, @Param("token") UUID token,
                             @Param("leaseMs") long leaseMs);

    @Query(value = """
            SELECT EXISTS (SELECT 1 FROM payments
                WHERE id = :id AND reconciliation_claim_token = :token
                  AND reconciliation_claim_until > CURRENT_TIMESTAMP AND status = 'PENDING')
            """, nativeQuery = true)
    boolean ownsReconciliationClaim(@Param("id") long id, @Param("token") UUID token);

    @Modifying
    @Query(value = """
            UPDATE payments SET reconciliation_claim_token = NULL, reconciliation_claim_until = NULL,
                reconciliation_next_attempt_at = CURRENT_TIMESTAMP + (:retryMs * INTERVAL '1 millisecond')
            WHERE id = :id AND reconciliation_claim_token = :token
            """, nativeQuery = true)
    int releaseReconciliation(@Param("id") long id, @Param("token") UUID token,
                              @Param("retryMs") long retryMs);

    Optional<Payment> findByOrderIdAndStatus(Long orderId, EPaymentStatus status);
    boolean existsByOrder_IdAndStatus(Long orderId, EPaymentStatus status);
    long countByOrderIdAndStatus(
            Long orderId,
            EPaymentStatus status
    );
}
