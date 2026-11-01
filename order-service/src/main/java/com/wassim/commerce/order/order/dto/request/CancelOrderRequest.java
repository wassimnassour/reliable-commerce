package com.wassim.commerce.order.order.dto.request;

import jakarta.validation.constraints.NotNull;

public record CancelOrderRequest(
        @NotNull Long orderId
        ) {
}
