package com.wassim.commerce.order.messaging.config;

import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

@Component("messagingDestinations")
public class MessagingDestinations {
    private final Map<String, String> exchanges;
    private final Map<String, String> queues;
    private final Map<String, Set<String>> routingKeys;

    public MessagingDestinations(RabbitMqConfigProperties properties) {
        Map<String, String> exchangeNames = new HashMap<>();
        Map<String, String> queueNames = new HashMap<>();
        Map<String, Set<String>> keysByExchange = new HashMap<>();
        for (var exchange : properties.getExchanges()) {
            if (keysByExchange.containsKey(exchange.getKey())) {
                throw new IllegalArgumentException("Duplicate exchange key: " + exchange.getKey());
            }
            addDestination(exchangeNames, exchange.getKey(), exchange.getName(), "exchange");
            Set<String> keys = new HashSet<>();
            for (var subscription : exchange.getSubscriptions()) {
                addDestination(queueNames, subscription.getKey(), subscription.getQueue(), "queue");
                for (String key : subscription.getRoutingKeys()) {
                    requireText(key, "routing key");
                    keys.add(key);
                }
            }
            keysByExchange.put(exchange.getKey(), Set.copyOf(keys));
        }
        this.exchanges = Map.copyOf(exchangeNames);
        this.queues = Map.copyOf(queueNames);
        this.routingKeys = Map.copyOf(keysByExchange);
    }

    public String exchange(String key) {
        return resolve(exchanges, key, "exchange");
    }

    public String queue(String key) {
        return resolve(queues, key, "queue");
    }

    public String routingKey(String exchangeKey, String key) {
        exchange(exchangeKey);
        if (key == null || !routingKeys.get(exchangeKey).contains(key)) {
            throw new IllegalArgumentException("Unknown routing key '" + key + "' for exchange '" + exchangeKey + "'");
        }
        return key;
    }

    public String ordersExchange() {
        return exchange("orders");
    }

    public String orderCreatedRoutingKey() {
        return routingKey("orders", "order.created");
    }
    public String orderCancelledRoutingKey() {
        return routingKey("orders", "order.cancelled");
    }


    public String fulfillmentOrdersQueue() {
        return queue("fulfillment-orders");
    }

    private static String resolve(Map<String, String> destinations, String key, String kind) {
        String name = key == null ? null : destinations.get(key);
        if (name == null) {
            throw new IllegalArgumentException("Unknown " + kind + " key: " + key);
        }
        return name;
    }

    private static void addDestination(Map<String, String> destinations, String key, String name, String kind) {
        requireText(key, kind + " key");
        requireText(name, kind + " name");
        String previous = destinations.putIfAbsent(key, name);
        if (previous != null && !previous.equals(name)) {
            throw new IllegalArgumentException("Conflicting " + kind + " key: " + key);
        }
    }

    private static void requireText(String value, String description) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Messaging " + description + " must not be blank");
        }
    }
}
