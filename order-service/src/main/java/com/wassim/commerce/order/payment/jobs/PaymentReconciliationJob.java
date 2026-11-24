package com.wassim.commerce.order.payment.jobs;

import com.wassim.commerce.order.payment.enums.EPaymentStatus;
import com.wassim.commerce.order.payment.provider.PaymentProvider;
import com.wassim.commerce.order.payment.service.PaymentReconciliationClaims;
import com.wassim.commerce.order.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentReconciliationJob {
    private final PaymentReconciliationClaims claims;
    private final PaymentProvider paymentProvider;
    private final PaymentService paymentService;

    @Value("${payment.reconciliation.minimum-age:PT60S}")
    private Duration minimumAge;
    @Value("${payment.reconciliation.claim-duration:PT5M}")
    private Duration claimDuration;
    @Value("${payment.reconciliation.retry-delay:PT30S}")
    private Duration retryDelay;
    @Value("${payment.reconciliation.batch-size:20}")
    private int batchSize;

    @Scheduled(fixedDelayString = "${payment.reconciliation.poll-delay-ms:30000}",
            initialDelayString = "${payment.reconciliation.poll-delay-ms:30000}")
    public synchronized void run() {
        for (var claim : claims.claim(minimumAge, claimDuration, batchSize)) {
            try {
                reconcile(claim);
            } catch (Exception exception) {
                log.warn("Payment reconciliation failed: paymentId={}, orderId={}, reason={}",
                        claim.paymentId(), claim.orderId(), exception.getMessage());
            } finally {
                release(claim);
            }
        }
    }

    private void reconcile(PaymentReconciliationClaims.Claim claim) {
        if (!claims.isOwned(claim)) {
            return;
        }
        // The claim transaction has committed; provider I/O holds no row locks.
        EPaymentStatus providerStatus = paymentProvider.getStatus(claim.paymentId());
        if (providerStatus == EPaymentStatus.PENDING) {
            return;
        }
        if (providerStatus != EPaymentStatus.SUCCEEDED && providerStatus != EPaymentStatus.FAILED) {
            log.warn("Unexpected provider status {} for payment {}", providerStatus, claim.paymentId());
            return;
        }
        // Completion still uses the Order lock and duplicate-result checks for callback races.
        if (claims.isOwned(claim)) {
            paymentService.completePayment(claim.paymentId(), providerStatus);
        }
    }

    private void release(PaymentReconciliationClaims.Claim claim) {
        try {
            claims.release(claim, retryDelay);
        } catch (Exception exception) {
            log.warn("Could not release payment claim {}; it will expire: {}",
                    claim.paymentId(), exception.getMessage());
        }
    }
}
