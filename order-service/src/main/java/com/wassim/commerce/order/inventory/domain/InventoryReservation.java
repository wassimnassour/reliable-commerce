package com.wassim.commerce.order.inventory.domain;

import com.wassim.commerce.order.inventory.enums.ReservationStatus;
import com.wassim.commerce.order.product.domain.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Getter
@Setter
@Entity
@Table(name = "inventory_reservations", uniqueConstraints = @UniqueConstraint(
        name = "uq_inventory_reservation_order_product", columnNames = {"order_id", "product_id"}))
public class InventoryReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long orderId;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, insertable = false, updatable = false)
    private Product product;

    @Column(nullable = false)
    private Instant expiresAt;
}
