package com.wassim.commerce.order.order.dto.response;

public record CreateOrderResponse (
        Long orderId,
        Integer totalQuantity,
        Integer totalAmount
){
}
