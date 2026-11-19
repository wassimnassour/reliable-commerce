package com.wassim.commerce.order.inventory.persistence;

import com.wassim.commerce.order.inventory.domain.InventoryReservation;
import com.wassim.commerce.order.order.enums.EOrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
    SELECT i FROM InventoryReservation i
    WHERE i.order.id = :orderId AND i.product.id = :productId
    """)
    Optional<InventoryReservation> findForUpdate(@Param("orderId") Long orderId,
                                              @Param("productId") Long productId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
    SELECT i FROM InventoryReservation i WHERE i.order.id = :orderId
    """)
    Optional<InventoryReservation> findByOrderId(@Param("orderId") Long orderId);


    @Query(
            value = """
SELECT o.id
FROM orders o
WHERE o.order_status = 'PENDING_PAYMENT'
  AND EXISTS (
      SELECT 1
      FROM inventory_reservations ir
      WHERE ir.order_id = o.id
        AND ir.status = 'RESERVED'
        AND ir.expires_at <= NOW()
  )
ORDER BY o.id
FOR UPDATE OF o SKIP LOCKED
LIMIT 20;
             """,
            nativeQuery = true
    )
    List<Long> findExpiredReservations();


    @Query("""
    SELECT r
    FROM InventoryReservation r
    WHERE r.order.id = :orderId
    ORDER BY r.product.id
    """)
    List<InventoryReservation> findAllByOrderId(
            @Param("orderId") Long orderId
    );


}


