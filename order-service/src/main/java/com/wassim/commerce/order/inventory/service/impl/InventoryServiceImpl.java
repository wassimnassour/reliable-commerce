package com.wassim.commerce.order.inventory.service.impl;

import com.wassim.commerce.order.exaptions.InsufficientStockException;
import com.wassim.commerce.order.inventory.domain.InventoryReservation;
import com.wassim.commerce.order.inventory.enums.EReservationStatus;
import org.springframework.beans.factory.annotation.Value;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

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
        reservation.setStatus(EReservationStatus.RESERVED);
        reservation.setExpiresAt(Instant.now().plus(reservationTtl));
        inventoryReservationRepository.save(reservation);
    }

    @Override
    @Transactional
    public void release(Long orderId, Long productId) {
        InventoryReservation reservation = inventoryReservationRepository.findForUpdate(orderId, productId)
                .orElseThrow(() -> new IllegalStateException("Reservation not found for order " + orderId
                        + " and product " + productId));
        if (reservation.getStatus() == EReservationStatus.RELEASED
                || reservation.getStatus() == EReservationStatus.EXPIRED) {
            return;
        }
        if (reservation.getStatus() != EReservationStatus.RESERVED) {
            throw new IllegalStateException("Cannot release a confirmed reservation");
        }
        if (inventoryRepository.releaseStock(productId, reservation.getQuantity()) != 1) {
            throw new IllegalStateException("Cannot release reserved stock for product " + productId);
        }
        reservation.setStatus(EReservationStatus.RELEASED);
    }

    @Override
    @Transactional
    public void confirm(Long orderId) {
        List<InventoryReservation> reservations = inventoryReservationRepository.findAllByOrderIdForUpdate(orderId);
        for (InventoryReservation reservation : reservations) {
            if (reservation.getStatus() == EReservationStatus.CONFIRMED) {
                continue;
            }
            if (reservation.getStatus() != EReservationStatus.RESERVED) {
                throw new IllegalStateException("Cannot confirm a released or expired reservation");
            }
            Long productId = reservation.getProduct().getId();
            if (inventoryRepository.confirmStock(productId, reservation.getQuantity()) != 1) {
                throw new IllegalStateException("Cannot confirm reserved stock for product " + productId);
            }
            reservation.setStatus(EReservationStatus.CONFIRMED);
        }
    }
}
