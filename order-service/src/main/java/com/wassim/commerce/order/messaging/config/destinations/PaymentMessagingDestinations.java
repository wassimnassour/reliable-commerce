package com.wassim.commerce.order.messaging.config.destinations;

import org.springframework.stereotype.Component;

import com.wassim.commerce.order.messaging.config.MessagingDestinations;

import lombok.RequiredArgsConstructor;

@Component 
@RequiredArgsConstructor 
public class PaymentMessagingDestinations {

    private final MessagingDestinations destinations;

    public String exchange() {
        return destinations.exchange("payments");
    }

    public String completedRoutingKey() {
        return destinations.routingKey(
            "payments",
            "payment.completed"
        );
    }

    public String fulfillmentQueue() {
        return destinations.queue("fulfillment-payments");
    }
}