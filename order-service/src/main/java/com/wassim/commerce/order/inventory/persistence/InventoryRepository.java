package com.wassim.commerce.order.inventory.persistence;

import com.wassim.commerce.order.inventory.domain.Inventory;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;

public interface InventoryRepository extends JpaRepository<Inventory, Long> {
    @Query("select i from Inventory i where i.product.id = :productId")
    Optional<Inventory> findByProductId(@Param("productId") Long productId);

    @Modifying
    @Query(value = """
    UPDATE inventory
    SET reserved_quantity = reserved_quantity + :quantity,
        version = version + 1
    WHERE product_id = :productId
      AND :quantity > 0
      AND total_quantity - reserved_quantity >= :quantity
      AND EXISTS (SELECT 1 FROM products WHERE id = :productId AND status = true)
    """, nativeQuery = true)
    int reserveStock(
            @Param("productId") Long productId,
            @Param("quantity") int quantity
    );
    @Modifying
    @Query(value = """
            UPDATE inventory
            SET reserved_quantity = reserved_quantity - :quantity, version = version + 1
            WHERE product_id = :productId AND :quantity > 0 AND reserved_quantity >= :quantity
            """, nativeQuery = true)
    int releaseStock(@Param("productId") Long productId, @Param("quantity") int quantity);
    @Modifying
    @Query(value = """
            UPDATE inventory
            SET total_quantity = total_quantity - :quantity,
                reserved_quantity = reserved_quantity - :quantity,
                version = version + 1
            WHERE product_id = :productId AND :quantity > 0
              AND reserved_quantity >= :quantity AND total_quantity >= :quantity
            """, nativeQuery = true)
    int confirmStock(@Param("productId") Long productId, @Param("quantity") int quantity);
}

