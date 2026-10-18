package com.wassim.commerce.order.product.api.dto.response;

import java.util.List;

public record ProductSliceResponse(List<ProductResponse> content, int page, int size, boolean hasNext) {
}
