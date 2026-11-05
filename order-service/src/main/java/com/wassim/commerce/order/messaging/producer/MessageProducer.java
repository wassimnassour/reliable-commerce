package com.wassim.commerce.order.messaging.producer;


import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class MessageProducer {
     private final RabbitTemplate rabbitTemplate;


     public MessageProducer(RabbitTemplate rabbitTemplate) {
         this.rabbitTemplate = rabbitTemplate;
     }

     public CompletableFuture<CorrelationData.Confirm> sendMessage(String exchange, String routingKey, Object message , String messageId) {
         CorrelationData correlationData = new CorrelationData(messageId);
         System.out.println("Sending message: " + message);
         rabbitTemplate.convertAndSend(exchange, routingKey, message , m -> {
             m.getMessageProperties().setMessageId(messageId);
             return m;
         },correlationData);
         return correlationData.getFuture();
     }

}
