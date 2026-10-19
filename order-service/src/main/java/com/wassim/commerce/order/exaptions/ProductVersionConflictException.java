package com.wassim.commerce.order.exaptions;

public class ProductVersionConflictException extends RuntimeException {
    public ProductVersionConflictException() {
        super("Product version is stale");
    }
}
