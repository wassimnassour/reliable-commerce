package com.wassim.commerce.order.auth.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LogoutRequestDto(
        @NotBlank(message = "Refresh token is required")
        String refreshToken
) {

}


