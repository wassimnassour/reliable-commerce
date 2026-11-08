package com.wassim.commerce.order.messaging.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Data
@ConfigurationProperties(prefix = "commerce.messaging")
public class RabbitMqConfigProperties {

    private List<ExchangeConfig> exchanges = new ArrayList<>();
    @Data
    public static class ExchangeConfig {
        private String key;
        private String name;
        private String type;
        private List<Subscription> subscriptions = new ArrayList<>();
    }


    @Data
    public static class Subscription {
        private String key;
        private String queue;
        private List<String> routingKeys = new ArrayList<>();
        private Map<String, Object> arguments = new HashMap<>();
    }
}
