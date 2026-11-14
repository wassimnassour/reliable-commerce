package com.wassim.commerce.order.messaging.handlers.PaymentHandlers;

import com.wassim.commerce.order.messaging.config.destinations.PaymentMessagingDestinations;
import com.wassim.commerce.order.messaging.inbox.persistence.InboxRepository;
import com.wassim.commerce.order.messaging.outbox.events.PaymentCompletedEvent;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.wassim.commerce.order.messaging.handlers.EventHandler;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Component 
public class PaymentCompletedEventHandler implements EventHandler {
    private final ObjectMapper objectMapper;
    Logger log =  LoggerFactory.getLogger(PaymentCompletedEventHandler.class);
    private final PaymentMessagingDestinations paymentMessagingDestinations;
    private final InboxRepository inboxRepository;

    @Override
	public String routingKey() {
		return paymentMessagingDestinations.completedRoutingKey();
	}

	@Override
    public void handle(String payload ,String messageId) {
        System.out.println("Recieved PaymentCompletedEvent" + payload);
        String consumerName = paymentMessagingDestinations.fulfillmentQueue();
         int claimed = inboxRepository.tryClaim(messageId ,consumerName , payload );
        if (claimed==0) {
            log.warn("Message [{}] already processed by [{}]. Skipping duplicate.", messageId, consumerName);
            return;
        }

        try{
            PaymentCompletedEvent event = objectMapper.readValue(payload, PaymentCompletedEvent.class);
            log.info("Processing order cancellation for order ID: {}",  event.orderId());

        } catch (JacksonException e) {
            throw new RuntimeException(e);
        }


        // No handling behavior is defined yet.
	}
}
