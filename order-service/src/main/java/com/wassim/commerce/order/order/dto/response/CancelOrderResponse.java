package com.wassim.commerce.order.order.dto.response;

import com.wassim.commerce.order.order.enums.EOrderStatus;

public record CancelOrderResponse (
        Long id ,
        EOrderStatus status
){
}
