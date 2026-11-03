package com.wassim.commerce.order.config;

import com.wassim.commerce.order.messaging.config.RabbitMqConfigProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.core.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.List;

@EnableConfigurationProperties(RabbitMqConfigProperties.class)
@RequiredArgsConstructor
@Configuration
public class RabbitMqConfig {

    private final RabbitMqConfigProperties  rabbitMqConfigProperties;

    @Bean
    public Declarables rabbitMQDeclarables() {
        List<Declarable> declarables = new ArrayList<>();

        rabbitMqConfigProperties.getExchanges().forEach(config -> {
            Exchange exchange = switch (config.getType()) {
                case "topic" ->
                        new TopicExchange(config.getName(), true, false);

                case "direct" ->
                        new DirectExchange(config.getName(), true, false);

                default ->
                        throw new IllegalArgumentException(
                                "Unsupported exchange type: " + config.getType()
                        );
            };

            declarables.add(exchange);

            config.getSubscriptions().forEach(subscription -> {
                Queue queue = QueueBuilder
                        .durable(subscription.getQueue())
                        .quorum()
                        .build();

                declarables.add(queue);

                subscription.getRoutingKeys().forEach(routingKey -> {
                    Binding binding = new Binding(
                            queue.getName(),
                            Binding.DestinationType.QUEUE,
                            exchange.getName(),
                            routingKey,
                            null
                    );

                    declarables.add(binding);
                });
            });
        });

        return new Declarables(declarables);
    }
}
