package com.wassim.commerce.order.exaptions;


import org.jspecify.annotations.Nullable;
import org.springframework.dao.DataIntegrityViolationException;

public class BadRequest extends RuntimeException{
    public BadRequest(@Nullable String msg) {
        super(msg);
    }
}
