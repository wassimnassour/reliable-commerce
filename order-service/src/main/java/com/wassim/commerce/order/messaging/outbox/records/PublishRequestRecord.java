package com.wassim.commerce.order.messaging.outbox.records;

import lombok.Builder;

@Builder
public record PublishRequestRecord(
        String aggregateType,
        String aggregateId,
        String exchange,
        String routingKey,
        Object payload
) {
}
