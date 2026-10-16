package com.wassim.commerce.order.product.api.dto.response;

import com.wassim.commerce.order.enums.Currency;

public record CreateProductResponse(
        Long id,
        String productName,
        String productDescription,
        Double productPrice,
        Integer productQuantity,
        Currency currency
) {
}
