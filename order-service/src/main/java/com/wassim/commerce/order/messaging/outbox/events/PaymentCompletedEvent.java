package com.wassim.commerce.order.messaging.outbox.events;

import com.wassim.commerce.order.payment.enums.EPaymentStatus;

import lombok.Builder;

@Builder 
public record PaymentCompletedEvent(
        Long paymentId,
        Long orderId,
        EPaymentStatus status
) {}