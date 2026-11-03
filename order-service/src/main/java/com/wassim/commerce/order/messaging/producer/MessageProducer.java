package com.wassim.commerce.order.messaging.producer;


import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
public class MessageProducer {
     private final RabbitTemplate rabbitTemplate;

     public MessageProducer(RabbitTemplate rabbitTemplate) {
         this.rabbitTemplate = rabbitTemplate;
     }

     public void sendMessage(String exchange, String routingKey, Object message) {
         System.out.println("Sending message: " + message);
         rabbitTemplate.convertAndSend(exchange, routingKey, message);
     }

}
