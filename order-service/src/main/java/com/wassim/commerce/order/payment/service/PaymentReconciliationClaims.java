package com.wassim.commerce.order.payment.service;

import com.wassim.commerce.order.payment.persistence.PaymentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
public class PaymentReconciliationClaims {
    public record Claim(long paymentId, long orderId, UUID token) {}

    private final PaymentRepository paymentRepository;

    public List<Claim> claim(Duration minimumAge, Duration claimDuration, int batchSize) {
        if (minimumAge.isNegative() || claimDuration.isNegative() || claimDuration.isZero() || batchSize <= 0) {
            throw new IllegalArgumentException("Invalid reconciliation claim configuration");
        }
        var candidates = paymentRepository.findToReconcile(minimumAge.toMillis(), batchSize);
        if (candidates.isEmpty()) {
            return List.of();
        }
        UUID token = UUID.randomUUID();
        // Row locks remain held until the claim update commits with this transaction.
        var paymentIds = candidates.stream().map(PaymentRepository.ReconciliationCandidate::getId).toList();
        paymentRepository.claimReconciliation(paymentIds, token, claimDuration.toMillis());
        return candidates.stream().map(p -> new Claim(p.getId(), p.getOrderId(), token)).toList();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public boolean isOwned(Claim claim) {
        return paymentRepository.ownsReconciliationClaim(claim.paymentId(), claim.token());
    }

    public void release(Claim claim, Duration retryDelay) {
        if (retryDelay.isNegative()) {
            throw new IllegalArgumentException("Reconciliation retry delay cannot be negative");
        }
        paymentRepository.releaseReconciliation(claim.paymentId(), claim.token(), retryDelay.toMillis());
    }
}
