package com.wassim.commerce.order.product.api.dto.request;

import com.wassim.commerce.order.enums.Currency;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record CreateProductDto(
        @NotBlank @Size(max = 255) String name,
        @Size(max = 255) String description,
        @NotNull @PositiveOrZero Integer stockQuantity,
        @NotNull @Positive @Digits(integer = 17, fraction = 2) BigDecimal price,
        Currency currency
) {
    public CreateProductDto {
        if (currency == null) {
            currency = Currency.MAD;
        }
    }
}
