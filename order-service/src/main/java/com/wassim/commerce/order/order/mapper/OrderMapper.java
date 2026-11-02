package com.wassim.commerce.order.order.mapper;

import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.order.dto.response.CancelOrderResponse;
import com.wassim.commerce.order.order.dto.response.CreateOrderResponse;
import org.mapstruct.InheritInverseConfiguration;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface OrderMapper {
    @Mapping(source = "id" , target = "orderId")
    @Mapping(source = "totalPrice", target = "totalAmount")
    @Mapping(source = "orderStatus", target = "status")
    CreateOrderResponse toCreateOrderResponse(Order order);


    @Mapping(source="orderStatus" , target = "status")
    @InheritInverseConfiguration(
            name = "toCreateOrderResponse"
    )
    CancelOrderResponse toCancelOrderResponse(Order order);
}
