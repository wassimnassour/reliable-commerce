package com.wassim.commerce.order.payment.provider.impl;

import com.wassim.commerce.order.payment.enums.EPaymentStatus;
import com.wassim.commerce.order.payment.provider.PaymentProvider;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class FakePaymentProvider implements PaymentProvider {

    private final Map<Long, EPaymentStatus> statuses = new ConcurrentHashMap<>();

    public void setStatus(Long paymentId, EPaymentStatus status) {
        statuses.put(paymentId, status);
    }

    @Override
    public EPaymentStatus getStatus(Long paymentId) {
        return statuses.getOrDefault(paymentId, EPaymentStatus.PENDING);
    }
}
