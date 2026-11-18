package com.wassim.commerce.order.inventory.service.impl;

import com.wassim.commerce.order.exaptions.InsufficientStockException;
import com.wassim.commerce.order.inventory.domain.InventoryReservation;
import com.wassim.commerce.order.inventory.enums.ReservationStatus;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import com.wassim.commerce.order.inventory.persistence.InventoryRepository;
import com.wassim.commerce.order.inventory.persistence.InventoryReservationRepository;
import com.wassim.commerce.order.inventory.service.InventoryService;
import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.product.domain.Product;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {
    private final InventoryRepository inventoryRepository;
    private final InventoryReservationRepository inventoryReservationRepository;

    @Value("${inventory.reservation-ttl:PT15M}")
    private Duration reservationTtl;

    @Override
    @Transactional
    public void reserve(Order order , Product product , int quantity) {
        Long productId = product.getId();
        if (inventoryRepository.reserveStock(product.getId(), quantity) != 1) {
            throw new InsufficientStockException("Insufficient stock for product " + productId);
        }
        InventoryReservation  reservation= new InventoryReservation();
        reservation.setOrder(order);
        reservation.setProduct(product);
        reservation.setQuantity(quantity);
        reservation.setStatus(ReservationStatus.RESERVED);
        reservation.setExpiresAt(Instant.now().plus(reservationTtl));
        inventoryReservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public void release(Long orderId, Long productId) {
        InventoryReservation reservation = inventoryReservationRepository.findForUpdate(orderId, productId)
                .orElseThrow(() -> new IllegalStateException("Reservation not found for order " + orderId
                        + " and product " + productId));
        if (reservation.getStatus() == ReservationStatus.RELEASED
                || reservation.getStatus() == ReservationStatus.EXPIRED) {
            return;
        }
        if (reservation.getStatus() != ReservationStatus.RESERVED) {
            throw new IllegalStateException("Cannot release a confirmed reservation");
        }
        if (inventoryRepository.releaseStock(productId, reservation.getQuantity()) != 1) {
            throw new IllegalStateException("Cannot release reserved stock for product " + productId);
        }
        reservation.setStatus(ReservationStatus.RELEASED);
    }
}
