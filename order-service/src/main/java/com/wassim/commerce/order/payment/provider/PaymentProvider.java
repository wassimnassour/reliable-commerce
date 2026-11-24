package com.wassim.commerce.order.payment.provider;

import com.wassim.commerce.order.payment.enums.EPaymentStatus;

public interface PaymentProvider {
    EPaymentStatus getStatus(Long paymentId);
}
