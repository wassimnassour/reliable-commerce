package com.wassim.commerce.order.order.dto.response;

import com.wassim.commerce.order.order.enums.EOrderStatus;

import java.math.BigDecimal;

public record CreateOrderResponse (
        Long orderId,
        Integer totalQuantity,
        BigDecimal totalAmount,
        EOrderStatus status
){
}
