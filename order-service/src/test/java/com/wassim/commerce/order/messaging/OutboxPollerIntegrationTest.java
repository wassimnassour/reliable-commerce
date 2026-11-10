package com.wassim.commerce.order.messaging;

import com.wassim.commerce.order.TestcontainersConfiguration;
import com.wassim.commerce.order.fixtures.GenerateProducts;
import com.wassim.commerce.order.messaging.config.MessagingDestinations;
import com.wassim.commerce.order.messaging.inbox.persistence.InboxRepository;
import com.wassim.commerce.order.messaging.outbox.OutboxPoller;
import com.wassim.commerce.order.messaging.outbox.domain.OutboxMessage;
import com.wassim.commerce.order.messaging.outbox.enums.EOutboxStatus;
import com.wassim.commerce.order.messaging.outbox.presissetance.OutboxRepository;
import com.wassim.commerce.order.messaging.producer.MessageProducer;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;


@Import(TestcontainersConfiguration.class)
@DataJpaTest
@AutoConfigureTestDatabase(
        replace = AutoConfigureTestDatabase.Replace.NONE
)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class OutboxPollerIntegrationTest {

    @Autowired
    OutboxRepository outboxRepository;

    @Autowired
    ProductRepository productRepository;

    RabbitTemplate rabbitTemplate;
    OutboxPoller outboxPoller;

    @BeforeEach
    void setup() {
        outboxRepository.deleteAllInBatch();

        // Mock only the external RabbitMQ dependency
        rabbitTemplate = mock(RabbitTemplate.class);

        MessageProducer producer =
                new MessageProducer(rabbitTemplate);

        outboxPoller =
                new OutboxPoller(outboxRepository, producer);
    }

    @Test
    void rabbitMqNack_keepsPending_thenAckPublishes() {

        OutboxMessage event = createPendingEvent();
        Long eventId = event.getId();

        AtomicInteger attempts = stubConfirms(false, true);

        // First attempt: NACK
        outboxPoller.poll();

        OutboxMessage afterNack =
                outboxRepository.findById(eventId).orElseThrow();

        assertThat(afterNack.getStatus())
                .isEqualTo(EOutboxStatus.PENDING);

        assertThat(afterNack.getRetryCount())
                .isEqualTo(1);

        assertThat(attempts.get()).isEqualTo(1);

        // Second attempt: ACK
        outboxPoller.poll();

        OutboxMessage afterAck =
                outboxRepository.findById(eventId).orElseThrow();

        assertThat(afterAck.getStatus())
                .isEqualTo(EOutboxStatus.PUBLISHED);

        assertThat(afterAck.getRetryCount())
                .isEqualTo(1);

        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    public void  cancelOrder_transactionFails_rollsBackOrderAndOutbox(){
        Product product = GenerateProducts.generateGoodProduct();
        productRepository.save(product);



    }

    private AtomicInteger stubConfirms(boolean... responses) {

        AtomicInteger attempts = new AtomicInteger();

        doAnswer(invocation -> {

            int index = attempts.getAndIncrement();

            if (index >= responses.length) {
                throw new AssertionError(
                        "Unexpected publishing attempt: " + index
                );
            }

            boolean ack = responses[index];

            CorrelationData correlationData =
                    invocation.getArgument(
                            4, CorrelationData.class
                    );

            correlationData.getFuture().complete(
                    new CorrelationData.Confirm(
                            ack,
                            ack ? null : "Simulated NACK"
                    )
            );

            return null;

        }).when(rabbitTemplate).convertAndSend(
                anyString(),
                anyString(),
                any(),
                any(MessagePostProcessor.class),
                any(CorrelationData.class)
        );

        return attempts;
    }

    private OutboxMessage createPendingEvent() {

        OutboxMessage event = OutboxMessage.builder()
                .aggregateType("ORDER")
                .aggregateId(UUID.randomUUID().toString())
                .exchange("commerce.events")
                .routingKey("order.cancelled")
                .payload("{\"orderId\":33,\"userId\":333}")
                .status(EOutboxStatus.PENDING)
                .retryCount(0)
                .build();

        return outboxRepository.saveAndFlush(event);
    }


}
