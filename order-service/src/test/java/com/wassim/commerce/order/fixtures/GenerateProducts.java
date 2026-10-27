package com.wassim.commerce.order.fixtures;

import com.wassim.commerce.order.product.domain.Product;

import java.math.BigDecimal;

public class GenerateProducts {

    private static Product generateProductWithNeccery(){
        BigDecimal price = BigDecimal.valueOf(1250);
        Product product = new Product();
        product.setProductPrice(price);
        product.setProductName("Product Name");
        product.setProductDescription("Product Description");
        product.setProductQuantity(100);
        return product;
    };

    public static Product generateGoodProduct() {
        Product product = generateProductWithNeccery();
        product.setStatus(true);
        return product;
    }

    public static Product generateProductStatusDisabled() {
        Product product = generateProductWithNeccery();
        product.setStatus(false);
        return product;
    }
}
