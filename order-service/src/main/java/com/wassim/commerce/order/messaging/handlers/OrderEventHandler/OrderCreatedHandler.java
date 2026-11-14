package com.wassim.commerce.order.messaging.handlers.OrderEventHandler;

import com.wassim.commerce.order.messaging.config.destinations.OrderMessagingDestinations;
import com.wassim.commerce.order.messaging.handlers.EventHandler;
import com.wassim.commerce.order.messaging.inbox.domain.InboxMessage;
import com.wassim.commerce.order.messaging.inbox.persistence.InboxRepository;
import org.springframework.stereotype.Component;

@Component
public class OrderCreatedHandler implements EventHandler {
    private  final OrderMessagingDestinations messagingDestinations;
    private final InboxRepository inboxRepository;

    public OrderCreatedHandler(OrderMessagingDestinations messagingDestinations) {
        this.messagingDestinations = messagingDestinations;
    }


    @Override
    public String routingKey() {
        return messagingDestinations.createdRoutingKey();
    }

    @Override
    public void handle(String payload, String messageId) {
        inboxRepository.tryClaim(

        )
         System.out.println("OrderCreatedHandler received OrderCreatedEvent");
    }
}
