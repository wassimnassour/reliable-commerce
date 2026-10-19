package com.wassim.commerce.order.product.api.dto.request;

import com.wassim.commerce.order.enums.Currency;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;

public record UpdateProductDto(
        @Size(max = 255) String name,
        @Size(max = 255) String description,
        @Positive @Digits(integer = 17, fraction = 2) BigDecimal price,
        Currency currency,
        @NotNull
        Long version
) {
}
