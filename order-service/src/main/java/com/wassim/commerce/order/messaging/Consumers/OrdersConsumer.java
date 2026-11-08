package com.wassim.commerce.order.messaging.Consumers;

import com.wassim.commerce.order.messaging.handlers.EventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class OrdersConsumer {
    private final Map<String, EventHandler> handlersList = new HashMap<>();
    private final Logger log = LoggerFactory.getLogger(OrdersConsumer.class);

    public OrdersConsumer(List<EventHandler> handlers) {
        for (EventHandler handler : handlers) {
            handlersList.put(handler.routingKey(), handler);
        }
    }

    @RabbitListener(queues = "fulfillment.orders")
    public void receiveMessage(
            String payload,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId) {
        EventHandler handler = handlersList.get(routingKey);
        if (handler != null) {
            handler.handle(payload, messageId);
        } else {
            log.warn("No handler registered for routing key: {}", routingKey);
            throw new AmqpRejectAndDontRequeueException("Unknown routing key: " + routingKey);
        }
    }
}
