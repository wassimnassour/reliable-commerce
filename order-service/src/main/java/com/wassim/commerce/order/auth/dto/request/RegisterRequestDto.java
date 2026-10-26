package com.wassim.commerce.order.auth.dto.request;


import jakarta.validation.constraints.NotNull;

public record RegisterRequestDto(
     @NotNull String username,
     @NotNull       String password,
     @NotNull   String email
) {
}
