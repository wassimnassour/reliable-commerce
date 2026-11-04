package com.wassim.commerce.order.messaging.handlers;

public interface EventHandler {
      String routingKey();
      void  handle(String payload);
}
