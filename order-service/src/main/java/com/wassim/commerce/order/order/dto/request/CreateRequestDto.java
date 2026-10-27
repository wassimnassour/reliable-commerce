package com.wassim.commerce.order.order.dto.request;

import com.wassim.commerce.order.order.records.OrderRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record CreateRequestDto(
        @NotEmpty List<@Valid OrderRequest> products) {
}
