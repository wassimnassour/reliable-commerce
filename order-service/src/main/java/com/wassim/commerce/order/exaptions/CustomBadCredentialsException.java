package com.wassim.commerce.order.exaptions;

import org.jspecify.annotations.Nullable;
import org.springframework.security.authentication.BadCredentialsException;

public class CustomBadCredentialsException  extends BadCredentialsException {
    public CustomBadCredentialsException(@Nullable String msg) {
        super(msg);
    }
}
