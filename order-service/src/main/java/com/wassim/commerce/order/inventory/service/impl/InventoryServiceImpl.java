package com.wassim.commerce.order.inventory.service.impl;

import com.wassim.commerce.order.exaptions.InsufficientStockException;
import com.wassim.commerce.order.inventory.domain.InventoryReservation;
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
        inventoryReservationRepository.save(reservation);
    }

    @Transactional
    public void release(Long productId, int quantity) {
        if (inventoryRepository.increaseTheStock(quantity, productId) != 1) {
            throw new InsufficientStockException("Cannot restore stock for product " + productId);
        }
    }
}
