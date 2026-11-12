package com.wassim.commerce.order.payment.dto.request;

import com.wassim.commerce.order.payment.enums.EPaymentStatus;

public record PaymentResultRequestDto(EPaymentStatus result) {
}
