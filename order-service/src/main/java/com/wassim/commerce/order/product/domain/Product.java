package com.wassim.commerce.order.product.domain;

import com.wassim.commerce.order.inventory.domain.InventoryReservation;
import com.wassim.commerce.order.inventory.domain.Inventory;
import com.wassim.commerce.order.order.domain.OrderItems;
import com.wassim.commerce.order.enums.Currency;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.math.BigDecimal;

import java.util.List;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "products")
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String productName;
    private String productDescription;
    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal productPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 3)
    private Currency currency = Currency.MAD;

    @OneToMany(mappedBy = "product")
    private List<OrderItems> orderItems;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private Instant updatedAt;

    private  Boolean status;

    @Version
    private Long version;

    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true)
    private Inventory inventory;

    @OneToMany(mappedBy = "product")
    private List<InventoryReservation> inventoryReservations;

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
