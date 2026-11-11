package com.wassim.commerce.order.payment.api;

import com.wassim.commerce.order.payment.dto.response.PaymentResponse;
import com.wassim.commerce.order.payment.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PaymentController {
    private final PaymentService paymentService;

    @PostMapping("/orders/{orderId}/payments")
    public PaymentResponse initiatePayment(
            @PathVariable Long orderId,
            @AuthenticationPrincipal Jwt jwt
    ) {
        return paymentService.initiatePayment(orderId, Long.valueOf(jwt.getSubject()));
    }
}
