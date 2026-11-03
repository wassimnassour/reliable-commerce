package com.wassim.commerce.order.messaging;

import com.wassim.commerce.order.config.RabbitMqConfig;
import com.wassim.commerce.order.messaging.config.MessagingDestinations;
import com.wassim.commerce.order.messaging.config.RabbitMqConfigProperties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.*;

class MessagingDestinationsTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withInitializer(new ConfigDataApplicationContextInitializer())
            .withUserConfiguration(RabbitMqConfig.class, MessagingDestinations.class);

    @Test
    void loadsYamlAndResolvesTheSameNamesAsTheDeclaredTopology() {
        contextRunner.run(context -> {
            assertThat(context).hasNotFailed().hasSingleBean(MessagingDestinations.class);
            var destinations = context.getBean(MessagingDestinations.class);
            assertThat(destinations.ordersExchange()).isEqualTo("commerce.events");
            assertThat(destinations.exchange("payments")).isEqualTo("payment.events");
            assertThat(destinations.fulfillmentOrdersQueue()).isEqualTo("fulfillment.orders");
            assertThat(destinations.queue("notifications-orders")).isEqualTo("notifications.orders");
            assertThat(destinations.queue("analytics-orders")).isEqualTo("analytics.orders");
            assertThat(destinations.queue("fulfillment-payments")).isEqualTo("fulfillment.payments");
            assertThat(destinations.orderCreatedRoutingKey()).isEqualTo("order.created");
            assertThat(destinations.routingKey("payments", "payment.completed")).isEqualTo("payment.completed");
            assertThat(destinations.routingKey("orders", "order.*")).isEqualTo("order.*");

            var topology = context.getBean(Declarables.class);
            assertThat(topology.getDeclarablesByType(Exchange.class)).extracting(Exchange::getName)
                    .containsExactlyInAnyOrder(destinations.ordersExchange(), destinations.exchange("payments"));
            assertThat(topology.getDeclarablesByType(Queue.class)).hasSize(4)
                    .allSatisfy(queue -> {
                        assertThat(queue.isDurable()).isTrue();
                        assertThat(queue.getArguments()).containsEntry("x-queue-type", "quorum");
                    });
            assertThat(topology.getDeclarablesByType(Binding.class)).hasSize(6)
                    .anySatisfy(binding -> {
                        assertThat(binding.getExchange()).isEqualTo(destinations.ordersExchange());
                        assertThat(binding.getDestination()).isEqualTo(destinations.fulfillmentOrdersQueue());
                        assertThat(binding.getRoutingKey()).isEqualTo(destinations.orderCreatedRoutingKey());
                    });
        });
    }

    @Test
    void stableKeysResolveRenamedBrokerDestinations() {
        contextRunner.withPropertyValues(
                "commerce.messaging.exchanges[0].key=orders",
                "commerce.messaging.exchanges[0].name=custom.events",
                "commerce.messaging.exchanges[0].type=direct",
                "commerce.messaging.exchanges[0].subscriptions[0].key=fulfillment-orders",
                "commerce.messaging.exchanges[0].subscriptions[0].queue=custom.orders",
                "commerce.messaging.exchanges[0].subscriptions[0].routing-keys[0]=order.created"
        ).run(context -> {
            assertThat(context).hasNotFailed();
            var destinations = context.getBean(MessagingDestinations.class);
            assertThat(destinations.ordersExchange()).isEqualTo("custom.events");
            assertThat(destinations.fulfillmentOrdersQueue()).isEqualTo("custom.orders");
            assertThat(context.getBean(Declarables.class).getDeclarablesByType(Exchange.class))
                    .singleElement().isInstanceOf(org.springframework.amqp.core.DirectExchange.class);
        });
    }

    @Test
    void rejectsUnknownDestinationsAndRoutingKeysOnTheWrongExchange() {
        contextRunner.run(context -> {
            var destinations = context.getBean(MessagingDestinations.class);
            assertThatIllegalArgumentException().isThrownBy(() -> destinations.exchange("missing"))
                    .withMessageContaining("Unknown exchange key");
            assertThatIllegalArgumentException().isThrownBy(() -> destinations.queue("missing"))
                    .withMessageContaining("Unknown queue key");
            assertThatIllegalArgumentException().isThrownBy(() -> destinations.routingKey("payments", "order.created"))
                    .withMessageContaining("Unknown routing key");
        });
    }

    @Test
    void rejectsMissingKeysAndConflictingAliases() {
        var properties = new RabbitMqConfigProperties();
        var exchange = new RabbitMqConfigProperties.ExchangeConfig();
        exchange.setName("commerce.events");
        properties.getExchanges().add(exchange);
        assertThatIllegalArgumentException().isThrownBy(() -> new MessagingDestinations(properties))
                .withMessageContaining("exchange key must not be blank");

        exchange.setKey("orders");
        var first = new RabbitMqConfigProperties.Subscription();
        first.setKey("fulfillment-orders");
        first.setQueue("first.orders");
        var second = new RabbitMqConfigProperties.Subscription();
        second.setKey("fulfillment-orders");
        second.setQueue("second.orders");
        exchange.getSubscriptions().add(first);
        exchange.getSubscriptions().add(second);
        assertThatIllegalArgumentException().isThrownBy(() -> new MessagingDestinations(properties))
                .withMessageContaining("Conflicting queue key");
    }

    @Test
    void rejectsDuplicateExchangeKeys() {
        var properties = new RabbitMqConfigProperties();
        var exchange = new RabbitMqConfigProperties.ExchangeConfig();
        exchange.setKey("orders");
        exchange.setName("commerce.events");
        properties.getExchanges().add(exchange);
        properties.getExchanges().add(exchange);
        assertThatIllegalArgumentException().isThrownBy(() -> new MessagingDestinations(properties))
                .withMessageContaining("Duplicate exchange key");
    }
}
