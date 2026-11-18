package com.wassim.commerce.order.inventory.service;
import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.product.domain.Product;


public interface InventoryService {
 void  reserve(Order order , Product product ,int quantity);
 void release(Long productId, int quantity);
}