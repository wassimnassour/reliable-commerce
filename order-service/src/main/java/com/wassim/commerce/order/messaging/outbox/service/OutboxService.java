package com.wassim.commerce.order.messaging.outbox.service;

import com.wassim.commerce.order.messaging.mapper.OutboxMapper;
import com.wassim.commerce.order.messaging.outbox.domain.OutboxMessage;
import com.wassim.commerce.order.messaging.outbox.enums.EOutboxStatus;
import com.wassim.commerce.order.messaging.outbox.presissetance.OutboxRepository;
import com.wassim.commerce.order.messaging.outbox.records.PublishRequestRecord;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Service
public class OutboxService {
    private final ObjectMapper objectMapper;
    Logger logger = LoggerFactory.getLogger(OutboxService.class);
    private  final OutboxRepository outboxRepository;
    private final OutboxMapper outboxMapper;


    @Transactional(propagation = Propagation.MANDATORY)
    public void publish(PublishRequestRecord publishRequestRecord) {
        OutboxMessage outbox= outboxMapper.toOutboxPattern(publishRequestRecord);
        outboxRepository.save(outbox);
        logger.info("Saved outbox event for {} [ID: {}] with routingKey: {}", publishRequestRecord.aggregateType(), publishRequestRecord.aggregateId(), publishRequestRecord.routingKey());
    }
}
