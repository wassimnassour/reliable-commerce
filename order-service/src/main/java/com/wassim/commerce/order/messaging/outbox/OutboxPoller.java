package com.wassim.commerce.order.messaging.outbox;

import com.wassim.commerce.order.messaging.outbox.domain.OutboxMessage;
import com.wassim.commerce.order.messaging.outbox.enums.EOutboxStatus;
import com.wassim.commerce.order.messaging.outbox.presissetance.OutboxRepository;

import com.wassim.commerce.order.messaging.producer.MessageProducer;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.beans.factory.annotation.Qualifier;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

@Component
public class OutboxPoller {

    private final OutboxRepository outboxRepository;
    private final ThreadPoolExecutor outboxJobExecutor;
    private final MessageProducer messageProducer;
    Logger logger = LoggerFactory.getLogger(OutboxPoller.class);

    public OutboxPoller(OutboxRepository outboxRepository,
            @Qualifier("OutboxJobExecutor") ThreadPoolExecutor outboxJobExecutor,
            MessageProducer messageProducer) {
        this.outboxRepository = outboxRepository;
        this.outboxJobExecutor = outboxJobExecutor;
        this.messageProducer = messageProducer;
    }

    private int availableSlots() {
        return Math.max(
                0,
                outboxJobExecutor.getMaximumPoolSize()
                        - outboxJobExecutor.getActiveCount()
                        + outboxJobExecutor.getQueue().remainingCapacity());
    }

    @Scheduled(fixedDelayString = "${spring.scheduler.poolIntervalMs:5000}")
    public void poll() {
        Map<OutboxMessage, CorrelationData> pendingConfirms = new HashMap<>();

        int freeSlot = availableSlots();

        if (freeSlot <= 0) {
            return;
        }
        List<OutboxMessage> listMessagesToProcess = outboxRepository.findToProcess(EOutboxStatus.PENDING, 20);

        for (OutboxMessage message : listMessagesToProcess) {
            int updatedRows = outboxRepository.claimMessage(
                    message.getId(),
                    EOutboxStatus.CLAIMED,
                    EOutboxStatus.PENDING,
                    Instant.now()
            );

            if (updatedRows == 0) {
                logger.debug("Message {} claimed by another instance, skipping.", message.getId());
                continue;
            }

            CorrelationData  correlationData = new CorrelationData(String.valueOf( message.getId()));
           messageProducer.sendMessage(message.getExchange(),
                    message.getRoutingKey(), message.getPayload(), String.valueOf(message.getId()),correlationData);
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
                    if (confirm != null && entry.getValue().getReturned() == null &&  confirm.isAck()) {
                        message.setStatus(EOutboxStatus.PUBLISHED);
                        message.setProcessedAt(Instant.now());
                        message.setErrorMessage(null);
                        continue;
                    }
                } catch (Exception e) {
                    logger.error("Error retrieving confirmation for outbox message id: {}", message.getId(), e);
                }
            }

            int retries = message.getRetryCount() + 1;
            message.setRetryCount(retries);
            if (retries >= 3) {
                message.setStatus(EOutboxStatus.FAILED);
                message.setErrorMessage("Exceeded max retries waiting for broker ACK");
            }else{
                message.setStatus(EOutboxStatus.PENDING);
            }
        }

        outboxRepository.saveAll(pendingConfirms.keySet());
    }
}
