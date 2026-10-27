package com.wassim.commerce.order.order.dto.response;

import java.math.BigDecimal;

public record CreateOrderResponse (
        Long orderId,
        Integer totalQuantity,
        BigDecimal totalAmount
){
}
