package com.wassim.commerce.order.inventory.service;

import com.wassim.commerce.order.exaptions.InsufficientStockException;
import com.wassim.commerce.order.inventory.persistence.InventoryRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class InventoryService {
    private final InventoryRepository inventoryRepository;

    @Transactional
    public void reserve(Long productId, int quantity) {
        if (inventoryRepository.decreaseTheStock(quantity, productId) != 1) {
            throw new InsufficientStockException("Insufficient stock for product " + productId);
        }
    }

    @Transactional
    public void release(Long productId, int quantity) {
        if (inventoryRepository.increaseTheStock(quantity, productId) != 1) {
            throw new InsufficientStockException("Cannot restore stock for product " + productId);
        }
    }
}
