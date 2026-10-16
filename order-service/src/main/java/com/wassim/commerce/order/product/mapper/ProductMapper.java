package com.wassim.commerce.order.product.mapper;


import com.wassim.commerce.order.product.api.dto.request.CreateProductDto;
import com.wassim.commerce.order.product.api.dto.response.CreateProductResponse;
import com.wassim.commerce.order.product.domain.Product;
import org.mapstruct.Mapper;


@Mapper(componentModel = "spring")
public interface ProductMapper {
    Product toProduct(CreateProductDto productDto);
    CreateProductResponse toResponse(Product product);
}
