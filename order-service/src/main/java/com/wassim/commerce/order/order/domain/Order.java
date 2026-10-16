package com.wassim.commerce.order.order.domain;

import jakarta.persistence.*;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

import java.util.List;

@Data
@Entity
@Table(name = "orders")
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, updatable = false)
    private String orderNumber;
    @Column(nullable = false, updatable = false)
    private Instant orderDate;
    @Column(nullable = false)
    private String orderStatus;

    private Integer totalPrice;
    private Integer totalQuantity;

    @OneToMany(mappedBy = "order")
    private List<OrderItems> orderItems;

    @ManyToOne(optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant updatedAt;

    @PrePersist
    private void prePersist() {
        Instant now = Instant.now();
        if (orderDate == null) {
            orderDate = now;
        }
        if (orderNumber == null) {
            orderNumber = UUID.randomUUID().toString();
        }
        if (orderStatus == null) {
            orderStatus = "PLACED";
        }
        updatedAt = now;
    }

    @PreUpdate
    private void preUpdate() {
        updatedAt = Instant.now();
    }
}
