package com.wassim.commerce.order.common.idempotency;

public record IdempotencyRecord(
        String id,
        String  response,
        String  payload
) {
}
