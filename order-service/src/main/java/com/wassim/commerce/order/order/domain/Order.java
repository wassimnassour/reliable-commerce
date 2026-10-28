package com.wassim.commerce.order.order.domain;

import com.wassim.commerce.order.user.domain.User;
import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
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

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPrice;
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
