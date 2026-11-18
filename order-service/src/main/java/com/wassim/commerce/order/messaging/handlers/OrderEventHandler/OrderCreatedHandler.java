package com.wassim.commerce.order.messaging.handlers.OrderEventHandler;

import com.wassim.commerce.order.messaging.config.destinations.OrderMessagingDestinations;
import com.wassim.commerce.order.messaging.handlers.EventHandler;

import com.wassim.commerce.order.messaging.inbox.persistence.InboxRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Component
public class OrderCreatedHandler implements EventHandler {
    private  final OrderMessagingDestinations messagingDestinations;
    private final InboxRepository inboxRepository;




    @Override
    public String routingKey() {
        return messagingDestinations.createdRoutingKey();
    }

    @Transactional
    @Override
    public void handle(String payload, String messageId) {
        inboxRepository.tryClaim(
               messageId,
                messagingDestinations.fulfillmentQueue(),
                payload
        );
         System.out.println("OrderCreatedHandler received OrderCreatedEvent");
    }
}
