package com.wassim.commerce.order.product.mapper;

import com.wassim.commerce.order.product.api.dto.request.CreateProductDto;
import com.wassim.commerce.order.product.api.dto.request.UpdateProductDto;
import com.wassim.commerce.order.product.api.dto.response.ProductResponse;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.inventory.domain.Inventory;
import org.mapstruct.*;

@Mapper(componentModel = "spring")
public interface ProductMapper {

    @Mapping(target = "productName", source = "name")
    @Mapping(target = "productDescription", source = "description")
    @Mapping(target = "productPrice", source = "price")
    @Mapping(target = "inventory", ignore = true)
    @Mapping(target = "inventoryReservations", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    Product toProduct(CreateProductDto productDto);

    @AfterMapping
    default void initializeInventory(CreateProductDto dto, @MappingTarget Product product) {
        var inventory = new Inventory();
        inventory.setProduct(product);
        inventory.setTotalQuantity(dto.stockQuantity());
        product.setInventory(inventory);
    }

    @Mapping(target = "name", source = "productName")
    @Mapping(target = "description", source = "productDescription")
    @Mapping(target = "price", source = "productPrice")
    @Mapping(target = "stockQuantity", source = "inventory.availableQuantity", defaultValue = "0")
    ProductResponse toResponse(Product product);





    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "productPrice", source = "price")
    @Mapping(target = "productName", source = "name")
    @Mapping(target = "productDescription", source = "description")
    @Mapping(target = "status", source = "status")
    @Mapping(target = "inventory", ignore = true)
    @Mapping(target = "inventoryReservations", ignore = true)
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "orderItems", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "version", ignore = true)
    void updateProduct(@MappingTarget Product product, UpdateProductDto updateProductDto);
}
