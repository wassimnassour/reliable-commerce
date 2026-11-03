package com.wassim.commerce.order.messaging.outbox.events;

public record OrderCancelEvent(
        Long orderId,
        Long userId) {
}
