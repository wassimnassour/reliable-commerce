package com.wassim.commerce.order.payment.mapper;

import com.wassim.commerce.order.payment.domain.Payment;
import com.wassim.commerce.order.payment.dto.response.PaymentResponse;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface PaymentMapper {
    @Mapping(target = "paymentId", source = "id")
    @Mapping(target = "orderId", source = "order.id")
    PaymentResponse toResponse(Payment payment);
}
