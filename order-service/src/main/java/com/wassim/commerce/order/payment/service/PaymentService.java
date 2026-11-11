package com.wassim.commerce.order.payment.service;

import com.wassim.commerce.order.payment.dto.response.PaymentResponse;

public interface PaymentService {
    PaymentResponse initiatePayment(Long orderId, Long userId);
}
