package com.wassim.commerce.order.fixtures;

import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.order.domain.OrderItems;
import com.wassim.commerce.order.order.enums.EOrderStatus;
import com.wassim.commerce.order.order.presisstance.OrderItemsRepository;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@RequiredArgsConstructor
public class CommerceFixtures {
    private final UserRepository users;
    private final ProductRepository products;
    private final OrderRepository orders;
    private final OrderItemsRepository orderItems;

    public User createUser() {
        User user = GenerateUser.generateUser();
        user.setRoles(List.of());
        return users.save(user);
    }

    public Product createProduct() {
        return products.save(GenerateProducts.generateGoodProduct());
    }

    @Transactional
    public Order createOrder(User user, Product product, EOrderStatus status) {
        Order order = new Order();
        order.setUser(user);
        order.setOrderStatus(status);
        order.setCurrency(product.getCurrency());
        order.setTotalQuantity(1);
        order.setTotalPrice(product.getProductPrice());
        Order savedOrder = orders.save(order);

        OrderItems item = new OrderItems();
        item.setOrder(savedOrder);
        item.setProduct(product);
        item.setQuantity(1);
        item.setTotalPrice(product.getProductPrice());
        savedOrder.setOrderItems(List.of(orderItems.save(item)));
        return savedOrder;
    }
}
