package com.wassim.commerce.order.exaptions;


public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
