package com.wassim.commerce.order.messaging.handlers.OrderEventHandler;

import com.wassim.commerce.order.messaging.config.MessagingDestinations;
import com.wassim.commerce.order.messaging.handlers.EventHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class OrderCancelledHandler implements EventHandler {
    private final MessagingDestinations messagingDestinations;

    @Override
    public String routingKey() {
        return messagingDestinations.orderCancelledRoutingKey();
    }

    @Override
    public void handle(String payload) {
        System.out.println("Received routing key: From inside OrderCancelledHandler " + payload);
    }
}
