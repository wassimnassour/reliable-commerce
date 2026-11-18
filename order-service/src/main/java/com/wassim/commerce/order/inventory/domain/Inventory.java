package com.wassim.commerce.order.inventory.domain;

import com.wassim.commerce.order.product.domain.Product;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "inventory")
public class Inventory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Column(nullable = false)
    private Integer totalQuantity = 0;

    @Column(nullable = false)
    private Integer reservedQuantity = 0;

    public Integer getAvailableQuantity() {
        return totalQuantity - reservedQuantity;
    }

    @Version
    private Long version;
}
