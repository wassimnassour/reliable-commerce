package com.wassim.commerce.order.inventory.service;

import com.wassim.commerce.order.inventory.domain.InventoryReservation;
import com.wassim.commerce.order.inventory.persistence.InventoryReservationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class InventoryService {
    private final InventoryReservationRepository inventoryReservationRepository;

    @Transactional
    public void reserve(Long productId, int quantity) {
        InventoryReservation inventory = inventoryReservationRepository.findForUpdate(productId)
                .orElseThrow();

        if (inventory.getAvailableStock() < quantity) {
            throw new InsufficientStockException();
        }

        inventory.reserve(quantity);
    }
}
