package com.wassim.commerce.order.auth.dto.response;

public record AuthResponseDto (
        UserResponseDto user,
        String token,
        String refreshToken
){
}
