package com.wassim.commerce.order.auth.dto.request;

import org.jspecify.annotations.NonNull;

public record LoginRequestDto(
        @NonNull String username,
        @NonNull String password
) {
}
