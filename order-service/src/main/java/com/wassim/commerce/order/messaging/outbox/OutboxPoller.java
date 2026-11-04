package com.wassim.commerce.order.messaging.outbox;

import com.wassim.commerce.order.messaging.outbox.domain.OutboxMessage;
import com.wassim.commerce.order.messaging.outbox.enums.EOutboxStatus;
import com.wassim.commerce.order.messaging.outbox.presissetance.OutboxRepository;
import com.wassim.commerce.order.messaging.outbox.service.OutboxService;
import com.wassim.commerce.order.messaging.producer.MessageProducer;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.concurrent.ThreadPoolExecutor;

@Component
public class OutboxPoller {

    private final OutboxService outboxService;
    private final OutboxRepository outboxRepository;
    private final ThreadPoolExecutor outboxJobExecutor;
    private final String poolIntervalMs;
    private final MessageProducer messageProducer;

    public OutboxPoller(OutboxService outboxService,
                        OutboxRepository outboxRepository,
                        @Qualifier("OutboxJobExecutor") ThreadPoolExecutor outboxJobExecutor,
                        MessageProducer messageProducer,
                        @Value("${spring.scheduler.poolIntervalMs}") String poolIntervalMs) {
        this.outboxService = outboxService;
        this.outboxRepository = outboxRepository;
        this.outboxJobExecutor = outboxJobExecutor;
        this.poolIntervalMs = poolIntervalMs;
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
         System.out.println("---------");
         int freeSlot = availableSlots();

         if(freeSlot <= 0 ){
             return;
         }
         List<OutboxMessage> listMessagesToProcess=  outboxRepository.findToProcess(EOutboxStatus.PENDING , 20);

        for( OutboxMessage message : listMessagesToProcess){
            messageProducer.sendMessage(message.getExchange() , message.getRoutingKey() , message.getPayload());
        }

         System.out.println(listMessagesToProcess);

    }
}
