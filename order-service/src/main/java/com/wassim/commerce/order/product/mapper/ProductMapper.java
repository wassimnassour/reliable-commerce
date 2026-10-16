package com.wassim.commerce.order.product.mapper;

import com.wassim.commerce.order.product.api.dto.request.CreateProductDto;
import com.wassim.commerce.order.product.api.dto.response.CreateProductResponse;
import com.wassim.commerce.order.product.domain.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface ProductMapper {
    @Mapping(target = "productName", source = "name")
    @Mapping(target = "productDescription", source = "description")
    @Mapping(target = "productPrice", source = "price")
    @Mapping(target = "productQuantity", source = "stockQuantity")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toProduct(CreateProductDto productDto);

    @Mapping(target = "name", source = "productName")
    @Mapping(target = "description", source = "productDescription")
    @Mapping(target = "price", source = "productPrice")
    @Mapping(target = "stockQuantity", source = "productQuantity")
    CreateProductResponse toResponse(Product product);
}
