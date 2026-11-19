package com.wassim.commerce.order.inventory.persistence;

import com.wassim.commerce.order.inventory.domain.InventoryReservation;
import com.wassim.commerce.order.inventory.enums.ReservationStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
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

    boolean existsByOrder_IdAndStatusAndExpiresAtLessThanEqual(
            Long orderId, ReservationStatus status, Instant deadline);

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


