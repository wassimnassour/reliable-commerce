package com.wassim.commerce.order.product.api.dto.response;

import com.wassim.commerce.order.enums.Currency;
import java.math.BigDecimal;

public record ProductResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        Currency currency,
        Long version,
        Boolean status
) {
}
