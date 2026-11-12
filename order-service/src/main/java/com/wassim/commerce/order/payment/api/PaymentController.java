package com.wassim.commerce.order.payment.api;

import com.wassim.commerce.order.common.idempotency.IdempotencyService;
import com.wassim.commerce.order.payment.dto.request.PaymentResultRequestDto;
import com.wassim.commerce.order.payment.dto.response.PaymentResponse;
import com.wassim.commerce.order.payment.service.PaymentService;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;
    private final IdempotencyService idempotencyService;

    @PostMapping("/orders/{orderId}/payments")
    public PaymentResponse initiatePayment(
            @PathVariable Long orderId,
            @AuthenticationPrincipal Jwt jwt,
            @Size(max = 255) @Pattern(regexp = "(?sU).*\\S.*")
            @RequestHeader(name = "Idempotency-Key", required = false) String idempotencyKey
    ) {
        Long userId = Long.valueOf(jwt.getSubject());
        if (idempotencyKey == null) {
            return paymentService.initiatePayment(orderId, userId);
        }
        return idempotencyService.execute(userId, "POST /orders/{orderId}/payments", idempotencyKey,
                orderId, PaymentResponse.class, () -> paymentService.initiatePayment(orderId, userId));
    }
    @PostMapping("/api/dev/payments/{id}/complete")
    public void completePayment(
            @PathVariable Long id,
            @RequestBody PaymentResultRequestDto request
    ) {
        paymentService.completePayment(id, request.result());
    }

}
