package com.wassim.commerce.order.order.service;


import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.dto.response.CreateOrderResponse;

public interface OrderService {
    CreateOrderResponse createOrder(Long userId , CreateRequestDto createRequestDto);
}

