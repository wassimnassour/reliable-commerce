package com.wassim.commerce.order.messaging.mapper;

import com.wassim.commerce.order.messaging.outbox.domain.OutboxMessage;
import com.wassim.commerce.order.messaging.outbox.enums.EOutboxStatus;
import com.wassim.commerce.order.messaging.outbox.records.PublishRequestRecord;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.Named;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Mapper(componentModel = "spring")
public abstract class OutboxMapper {
    private static final Logger logger = LoggerFactory.getLogger(OutboxMapper.class);

    @Autowired  ObjectMapper objectMapper;


    @Mapping( target = "status", constant = "PENDING")
    @Mapping(source = "payload" , target = "payload" , qualifiedByName = "serializeToJson")
    public abstract OutboxMessage toOutboxPattern(PublishRequestRecord publishRequestRecord);

    @Named("serializeToJson")
    protected String serializeToJson(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException e) {
            logger.error("Failed to serialize outbox payload: {}", payload, e);
            throw new RuntimeException("Could not serialize outbox payload to JSON", e);
        }
    }
}
