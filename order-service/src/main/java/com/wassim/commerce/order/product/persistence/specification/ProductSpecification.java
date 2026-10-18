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

    public static Specification<Product> productPriceGreaterThan(Integer greaterThan) {
        return (root, query, cb) -> greaterThan == null
                ? cb.conjunction()
                : cb.greaterThan(root.<BigDecimal>get("productPrice"), BigDecimal.valueOf(greaterThan));
    }

    public static Specification<Product> productPriceLessThan(Integer lessThen) {
        return (root, query, cb) -> lessThen == null
                ? cb.conjunction()
                : cb.lessThan(root.<BigDecimal>get("productPrice"), BigDecimal.valueOf(lessThen));
    }
}
