package com.wassim.commerce.order.product.api.dto.request;


public record ProductFilter(
         String productName ,
         Integer page,
         Integer size,
         String sortBy,
         Integer greaterThan ,
         Integer lessThen
) {
    public ProductFilter{
        page = page ==null ? 0 : page;
        size = size== null ? 25: size;
    }
}
