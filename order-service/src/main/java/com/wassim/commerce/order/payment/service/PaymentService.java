package com.wassim.commerce.order.payment.service;

import com.wassim.commerce.order.payment.dto.response.PaymentResponse;
import com.wassim.commerce.order.payment.enums.EPaymentStatus;

public interface PaymentService {
    PaymentResponse initiatePayment(Long orderId, Long userId);
    void completePayment(Long paymentId ,  EPaymentStatus results);
}
