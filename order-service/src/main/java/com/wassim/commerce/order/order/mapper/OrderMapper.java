package com.wassim.commerce.order.order.mapper;

import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.order.dto.response.CreateOrderResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    @Mapping(source = "id" , target = "orderId")
    @Mapping(source = "totalPrice", target = "totalAmount")
    CreateOrderResponse toCreateOrderResponse(Order order);
}
