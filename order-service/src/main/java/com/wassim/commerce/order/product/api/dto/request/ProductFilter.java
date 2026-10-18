package com.wassim.commerce.order.product.api.dto.request;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record ProductFilter(
        String name,
        @Min(0) Integer page,
        @Min(1) @Max(100) Integer size,
        @Pattern(regexp = "(id|name|price)(,(asc|desc))?", message = "must be id, name or price with optional ,asc or ,desc")
        String sortBy,
        @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal minPrice,
        @DecimalMin("0") @Digits(integer = 17, fraction = 2) BigDecimal maxPrice
) {
    public ProductFilter {
        page = page == null ? 0 : page;
        size = size == null ? 20 : size;
        sortBy = sortBy == null ? "id,asc" : sortBy;
    }

    @AssertTrue(message = "minPrice must be less than or equal to maxPrice")
    public boolean isPriceRangeValid() {
        return minPrice == null || maxPrice == null || minPrice.compareTo(maxPrice) <= 0;
    }
}
