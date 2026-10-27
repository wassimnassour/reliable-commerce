package com.wassim.commerce.order.order.presisstance;

import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.order.domain.OrderItems;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order,Long> {
}
