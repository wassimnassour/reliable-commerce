package com.wassim.commerce.order.messaging.outbox.presissetance;

import com.wassim.commerce.order.messaging.outbox.domain.OutboxMessage;
import com.wassim.commerce.order.messaging.outbox.enums.EOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;


import jakarta.persistence.QueryHint;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.stereotype.Repository;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    @Query(value = """
        SELECT * FROM outbox_messages o
        WHERE o.status = :#{#status.name()}
        ORDER BY o.created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
    """, nativeQuery = true)
    List<OutboxMessage> findToProcess(
            @Param("status") EOutboxStatus status,
            @Param("limit") int limit
    );
}