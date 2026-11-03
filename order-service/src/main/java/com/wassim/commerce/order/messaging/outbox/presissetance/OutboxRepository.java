package com.wassim.commerce.order.messaging.outbox.presissetance;

import com.wassim.commerce.order.messaging.outbox.domain.OutboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {
}
