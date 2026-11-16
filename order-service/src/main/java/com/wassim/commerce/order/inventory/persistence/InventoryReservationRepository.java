package com.wassim.commerce.order.inventory.persistence;

import com.wassim.commerce.order.inventory.domain.InventoryReservation;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

@Repository
public interface InventoryReservationRepository extends JpaRepository<InventoryReservation, Long> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
    SELECT i FROM InventoryReservation i
    WHERE i.orderId = :orderId AND i.productId = :productId
    """)
    Optional<InventoryReservation> findForUpdate(@Param("orderId") Long orderId,
                                              @Param("productId") Long productId);
}


