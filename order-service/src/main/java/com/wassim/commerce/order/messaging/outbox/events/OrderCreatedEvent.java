package com.wassim.commerce.order.messaging.outbox.events;

public record OrderCreatedEvent (
        Long orderId
){
}
