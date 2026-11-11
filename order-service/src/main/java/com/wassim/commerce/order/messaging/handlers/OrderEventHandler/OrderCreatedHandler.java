package com.wassim.commerce.order.messaging.handlers.OrderEventHandler;

import com.wassim.commerce.order.messaging.config.MessagingDestinations;
import com.wassim.commerce.order.messaging.handlers.EventHandler;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedHandler implements EventHandler {
    private  final MessagingDestinations messagingDestinations;
    public OrderCreatedHandler(MessagingDestinations messagingDestinations) {
        this.messagingDestinations = messagingDestinations;
    }


    @Override
    public String routingKey() {
        return messagingDestinations.orderCreatedRoutingKey();
    }

    @Override
    public void handle(String payload, String messageId) {
         System.out.println("OrderCreatedHandler received OrderCreatedEvent");
    }
}
