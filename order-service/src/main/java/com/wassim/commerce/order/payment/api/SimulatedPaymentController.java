package com.wassim.commerce.order.payment.api;

import com.wassim.commerce.order.common.idempotency.IdempotencyService;
import com.wassim.commerce.order.payment.dto.request.PaymentResultRequestDto;
import com.wassim.commerce.order.payment.service.PaymentService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Profile({"local", "dev", "test"})
@RestController
@RequiredArgsConstructor
public class SimulatedPaymentController {
    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    @PostMapping("/api/dev/payments/{id}/complete")
    public void completePayment(
            @PathVariable("id") Long paymentId,
            @RequestBody PaymentResultRequestDto request,
            @Size(max = 255) @Pattern(regexp = "(?sU).*\\S.*")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt
    ) {
        if (idempotencyKey == null) {
            paymentService.completePayment(paymentId, request.result());
            return;
        }

        idempotencyService.execute(Long.valueOf(jwt.getSubject()), "POST /api/dev/payments/{id}/complete",
                idempotencyKey, Map.of("paymentId", paymentId, "request", request), Void.class, () -> {
                    paymentService.completePayment(paymentId, request.result());
                    return null;
                });
    }
}
