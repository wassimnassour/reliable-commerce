package com.wassim.commerce.order.order.service;


import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.dto.response.CancelOrderResponse;
import com.wassim.commerce.order.order.dto.response.CreateOrderResponse;

public interface OrderService {
    CreateOrderResponse createOrder(String orderIdempotencyKey ,  Long userId , CreateRequestDto createRequestDto);

    CancelOrderResponse cancelOrder(Long userId, Long orderId );
}

