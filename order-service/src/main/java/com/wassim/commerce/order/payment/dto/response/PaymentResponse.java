package com.wassim.commerce.order.payment.dto.response;

import com.wassim.commerce.order.payment.enums.EPaymentStatus;

import java.math.BigDecimal;

public record PaymentResponse(Long paymentId, Long orderId, BigDecimal amount, EPaymentStatus status) {
}
