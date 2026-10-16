package com.wassim.commerce.order.product;

import com.wassim.commerce.order.enums.Currency;
import com.wassim.commerce.order.product.api.dto.request.CreateProductDto;
import com.wassim.commerce.order.product.mapper.ProductMapper;
import jakarta.validation.Validation;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.util.Set;
import java.math.BigDecimal;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class ProductCreationTest {
    @Test
    void acceptsZeroStockAndDefaultsCurrencyToMad() {
        var request = new CreateProductDto("Coffee", "Beans", 0, new BigDecimal("25.00"), null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            assertTrue(factory.getValidator().validate(request).isEmpty());
        }
        assertEquals(Currency.MAD, request.currency());
    }

    @Test
    void rejectsBlankNameNegativeStockAndNonpositivePrice() {
        var request = new CreateProductDto("  ", null, -1, BigDecimal.ZERO, null);
        try (var factory = Validation.buildDefaultValidatorFactory()) {
            var fields = factory.getValidator().validate(request).stream()
                    .map(violation -> violation.getPropertyPath().toString())
                    .collect(Collectors.toSet());
            assertEquals(Set.of("name", "stockQuantity", "price"), fields);
        }
    }

    @Test
    void mapsProductFieldsToResponse() {
        var mapper = Mappers.getMapper(ProductMapper.class);
        var request = new CreateProductDto("Coffee", "Beans", 0, new BigDecimal("25.00"), null);
        var product = mapper.toProduct(request);
        product.setId(42L);
        var response = mapper.toResponse(product);
        assertEquals(42L, response.id());
        assertEquals("Coffee", response.name());
        assertEquals("Beans", response.description());
        assertEquals(new BigDecimal("25.00"), response.price());
        assertEquals(0, response.stockQuantity());
        assertEquals(Currency.MAD, response.currency());
    }
}
