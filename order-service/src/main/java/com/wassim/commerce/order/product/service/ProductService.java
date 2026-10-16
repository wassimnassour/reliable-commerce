package com.wassim.commerce.order.product.service;

import com.wassim.commerce.order.product.api.dto.request.CreateProductDto;
import com.wassim.commerce.order.product.api.dto.response.CreateProductResponse;

public interface ProductService {
    CreateProductResponse createProduct(CreateProductDto product);
}
