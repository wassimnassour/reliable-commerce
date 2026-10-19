package com.wassim.commerce.order.product.service;

import com.wassim.commerce.order.product.api.dto.request.CreateProductDto;
import com.wassim.commerce.order.product.api.dto.request.ProductFilter;
import com.wassim.commerce.order.product.api.dto.request.UpdateProductDto;
import com.wassim.commerce.order.product.api.dto.response.ProductResponse;

import com.wassim.commerce.order.product.api.dto.response.ProductSliceResponse;

public interface ProductService {
    ProductResponse createProduct(CreateProductDto product);
    ProductResponse getProductById(Long id);
    ProductSliceResponse  getAllProducts(ProductFilter productFilter);
    ProductResponse updateProduct(Long id, UpdateProductDto updateProductDto);
}
