package com.wassim.commerce.order.inventory.domain;

import com.wassim.commerce.order.inventory.enums.ReservationStatus;
import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.product.domain.Product;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Getter
@Setter
@Builder
@Entity
@Table(name = "inventory_reservations", uniqueConstraints = @UniqueConstraint(
        name = "uq_inventory_reservation_order_product", columnNames = {"order_id", "product_id"}))
@NoArgsConstructor
@AllArgsConstructor
public class InventoryReservation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;


    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ReservationStatus status;


    @Column(nullable = false)
    private Instant expiresAt;
}
