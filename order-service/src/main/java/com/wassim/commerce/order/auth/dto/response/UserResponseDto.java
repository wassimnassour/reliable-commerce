package com.wassim.commerce.order.auth.dto.response;



import java.time.Instant;
import java.util.List;

public record UserResponseDto(
        String email,
        Instant createdAt,
        Instant updatedAt,
        List<String> roles,
        List<String> permissions
) {
}
