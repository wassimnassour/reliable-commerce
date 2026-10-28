package com.wassim.commerce.order.order.records;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record OrderRequest (
        @NotNull Long productId,
        @NotNull @Positive Integer quantity,
        @NotBlank  String adress
){

}
