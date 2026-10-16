package com.wassim.commerce.order.product.domain;

import com.wassim.commerce.order.order.domain.OrderItems;
import com.wassim.commerce.order.enums.Currency;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;

import java.util.List;

@Data
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String productName;
    private String productDescription;
    private Double productPrice;
    private Integer productQuantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency = Currency.MAD;

    @OneToMany(mappedBy = "product")
    private List<OrderItems> orderItems;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void prePersist() {
        Instant now = Instant.now();
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = Instant.now();
    }
}
