package com.wassim.commerce.order.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import com.wassim.commerce.order.auth.validation.Utf8ByteLength;

public record LoginRequestDto(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(max = 72) @Utf8ByteLength(max = 72) String password
) {
}
