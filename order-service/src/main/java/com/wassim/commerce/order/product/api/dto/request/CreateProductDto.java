package com.wassim.commerce.order.product.api.dto.request;

import com.wassim.commerce.order.enums.Currency;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateProductDto(
        @NotBlank
        String productName,

        String  productDescription,

        @PositiveOrZero
        @NotNull
        Integer   productQuantity,
        @Positive
        @NotNull
        Double   productPrice,
        Currency currency
) {
    public CreateProductDto {
        if (currency == null) {
            currency = Currency.MAD;
        }
    }
}
