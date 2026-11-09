package com.wassim.commerce.order.messaging.handlers.OrderEventHandler;

import com.wassim.commerce.order.messaging.config.MessagingDestinations;
import com.wassim.commerce.order.messaging.handlers.EventHandler;
import com.wassim.commerce.order.messaging.inbox.persistence.InboxRepository;
import com.wassim.commerce.order.messaging.outbox.events.OrderCancelEvent;
import jakarta.transaction.Transactional;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderCancelledHandler implements EventHandler {

    private final MessagingDestinations messagingDestinations;
    private final ObjectMapper objectMapper;
    private final InboxRepository inboxRepository;

    @Override
    public String routingKey() {
        return messagingDestinations.orderCancelledRoutingKey();
    }

    @Override
    @Transactional
    public void handle(String payload ,String messageId) {
        if (messageId == null || messageId.isBlank() || messageId.length() > 100) {
            throw new AmqpRejectAndDontRequeueException("A message ID of 1 to 100 characters is required");
        }
        final String consumerName = "fulfillment-orders";
        int claimed = inboxRepository.tryClaim(
                messageId ,
                consumerName,
                payload
        );

        if (claimed==0) {
            log.warn("Message [{}] already processed by [{}]. Skipping duplicate.", messageId, consumerName);
            return;
        }

        try {
            OrderCancelEvent event = objectMapper.readValue(payload, OrderCancelEvent.class);
            log.info("Processing order cancellation for order ID: {}", event.orderId());


//            Ok We Can have Busnisse Logic here
//            send Emails
//            ......


        } catch (JacksonException e) {
            log.error("Failed to parse OrderCancelEvent JSON: {}", payload, e);
            throw new AmqpRejectAndDontRequeueException("Malformed event payload", e);
        }
    }
}