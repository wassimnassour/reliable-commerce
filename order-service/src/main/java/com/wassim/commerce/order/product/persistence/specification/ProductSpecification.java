package com.wassim.commerce.order.product.persistence.specification;

import com.wassim.commerce.order.product.domain.Product;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.Locale;

public class ProductSpecification {
    public static Specification<Product> productNameLike(String productName) {
        return (root, query, cb) -> {
            if (productName == null || productName.isBlank()) {
                return cb.conjunction();
            }
            return cb.like(cb.lower(root.get("productName")),
                    "%" + productName.toLowerCase(Locale.ROOT) + "%");
        };
    }

    public static Specification<Product> productPriceAtLeast(BigDecimal minPrice) {
        return (root, query, cb) -> minPrice == null
                ? cb.conjunction()
                : cb.greaterThanOrEqualTo(root.<BigDecimal>get("productPrice"), minPrice);
    }

    public static Specification<Product> productPriceAtMost(BigDecimal maxPrice) {
        return (root, query, cb) -> maxPrice == null
                ? cb.conjunction()
                : cb.lessThanOrEqualTo(root.<BigDecimal>get("productPrice"), maxPrice);
    }
}
