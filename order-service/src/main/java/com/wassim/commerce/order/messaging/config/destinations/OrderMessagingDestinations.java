package com.wassim.commerce.order.messaging.config.destinations;

import org.springframework.stereotype.Component;

import com.wassim.commerce.order.messaging.config.MessagingDestinations;

import lombok.RequiredArgsConstructor;

@Component 
@RequiredArgsConstructor 
public class OrderMessagingDestinations {

    private final MessagingDestinations destinations;

    public String exchange() {
        return destinations.exchange("orders");
    }

    public String createdRoutingKey() {
        return destinations.routingKey("orders", "order.created");
    }

    public String cancelledRoutingKey() {
        return destinations.routingKey("orders", "order.cancelled");
    }

    public String fulfillmentQueue() {
        return destinations.queue("fulfillment-orders");
    }
}