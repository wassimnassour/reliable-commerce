package com.wassim.commerce.order.messaging.outbox;

import com.wassim.commerce.order.messaging.outbox.domain.OutboxMessage;
import com.wassim.commerce.order.messaging.outbox.enums.EOutboxStatus;
import com.wassim.commerce.order.messaging.outbox.presissetance.OutboxRepository;

import com.wassim.commerce.order.messaging.producer.MessageProducer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final MessageProducer messageProducer;
    Logger logger = LoggerFactory.getLogger(OutboxPoller.class);

    public OutboxPoller(OutboxRepository outboxRepository,
            MessageProducer messageProducer) {
        this.outboxRepository = outboxRepository;
        this.messageProducer = messageProducer;
    }

    @Scheduled(fixedDelayString = "${spring.scheduler.poolIntervalMs:5000}")
    public void poll() {
        Map<OutboxMessage, CorrelationData> pendingConfirms = new HashMap<>();

        List<OutboxMessage> listMessagesToProcess = outboxRepository.findToProcess(Instant.now(), 20);

        for (OutboxMessage message : listMessagesToProcess) {
            Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
            Instant leaseUntil = now.plus(5, ChronoUnit.MINUTES);
            int updatedRows = outboxRepository.claimMessage(
                    message.getId(), EOutboxStatus.CLAIMED, EOutboxStatus.PENDING,
                    message.getRetryCount(), now, leaseUntil);

            if (updatedRows == 0) {
                logger.debug("Message {} claimed by another instance, skipping.", message.getId());
                continue;
            }

            message.setLeaseUntil(leaseUntil);
            CorrelationData correlationData = new CorrelationData(UUID.randomUUID().toString());
            try {
                messageProducer.sendMessage(message.getExchange(), message.getRoutingKey(),
                        message.getPayload(), String.valueOf(message.getId()), correlationData);
            } catch (RuntimeException e) {
                correlationData.getFuture().completeExceptionally(e);
            }
            pendingConfirms.put(message, correlationData);
        }

        try {
            CompletableFuture<?>[] futures = pendingConfirms.values().stream()
                    .map(CorrelationData::getFuture)
                    .toArray(CompletableFuture<?>[]::new);

            CompletableFuture.allOf(futures).get(5, TimeUnit.SECONDS);

        } catch (Exception e) {
            logger.warn("One or more messages timed out or failed to receive broker ACK");
        }

        for (Map.Entry<OutboxMessage, CorrelationData> entry : pendingConfirms.entrySet()) {
            OutboxMessage message = entry.getKey();
            CompletableFuture<CorrelationData.Confirm> future = entry.getValue().getFuture();

            if (future.isDone() && !future.isCompletedExceptionally()) {
                try {
                    CorrelationData.Confirm confirm = future.join();
                    if (confirm != null && entry.getValue().getReturned() == null && confirm.ack()) {
                        message.setStatus(EOutboxStatus.PUBLISHED);
                        message.setProcessedAt(Instant.now());
                        message.setErrorMessage(null);
                    }
                } catch (Exception e) {
                    logger.error("Error retrieving confirmation for outbox message id: {}", message.getId(), e);
                }
            }

            if (message.getStatus() == EOutboxStatus.PUBLISHED) {
                finishAttempt(message);
                continue;
            }

            int retries = message.getRetryCount() + 1;
            message.setRetryCount(retries);
            if (retries >= 3) {
                message.setStatus(EOutboxStatus.FAILED);
                message.setErrorMessage("Exceeded max retries waiting for broker ACK");
            } else {
                message.setStatus(EOutboxStatus.PENDING);
            }
            finishAttempt(message);
        }
    }

    private void finishAttempt(OutboxMessage message) {
        outboxRepository.finishAttempt(message.getId(), EOutboxStatus.CLAIMED, message.getLeaseUntil(),
                message.getStatus(), message.getRetryCount(), message.getProcessedAt(),
                message.getErrorMessage(), Instant.now());
    }
}
