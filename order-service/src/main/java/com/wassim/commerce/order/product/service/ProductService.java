package com.wassim.commerce.order.product.service;

import com.wassim.commerce.order.product.api.dto.request.CreateProductDto;
import com.wassim.commerce.order.product.api.dto.request.ProductFilter;
import com.wassim.commerce.order.product.api.dto.response.ProductResponse;

import java.util.List;

public interface ProductService {
    ProductResponse createProduct(CreateProductDto product);
    ProductResponse getProductById(Long id);
    List<ProductResponse>  getAllProducts(ProductFilter productFilter);
}
