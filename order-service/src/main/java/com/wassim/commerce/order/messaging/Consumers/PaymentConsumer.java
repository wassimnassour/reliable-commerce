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

@Component
public class PaymentConsumer {
    Logger log = LoggerFactory.getLogger(PaymentConsumer.class);
    HashMap<String ,EventHandler> handlerList  = new HashMap<>();

    public PaymentConsumer(List<EventHandler> listenerList) {
        listenerList.forEach(handler -> {
            handlerList.put(handler.routingKey(), handler);
        });
    }

    @RabbitListener(queues = "#{@PaymentMessagingDestinations.fulfillmentQueue()}")
    public void consumer(
            String payload,
            @Header(AmqpHeaders.RECEIVED_ROUTING_KEY) String routingKey,
            @Header(value = AmqpHeaders.MESSAGE_ID, required = false) String messageId    ){

        EventHandler handler = handlerList.get(routingKey);
        if (handler != null) {
            handler.handle(payload, messageId);
        }else{
            log.warn("No handler registered for routing key: {}", routingKey);
            throw new AmqpRejectAndDontRequeueException("Unknown routing key: " + routingKey);
        }

    }
}
