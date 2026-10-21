package com.wassim.commerce.order.auth.dto.response;


import java.time.Instant;

public record UserResponseDto(
        String email,
        Instant createdAt,
        Instant updatedAt
) {
}
