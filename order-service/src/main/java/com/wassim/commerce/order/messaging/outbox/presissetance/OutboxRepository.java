package com.wassim.commerce.order.messaging.outbox.presissetance;

import com.wassim.commerce.order.messaging.outbox.domain.OutboxMessage;
import com.wassim.commerce.order.messaging.outbox.enums.EOutboxStatus;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    @Query(value = """
        SELECT * FROM outbox_messages o
        WHERE o.status = 'PENDING'
           OR (o.status = 'CLAIMED' AND o.lease_until <= :now)
        ORDER BY o.created_at ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<OutboxMessage> findToProcess(@Param("now") Instant now, @Param("limit") int limit);

    @Transactional(propagation = Propagation.REQUIRED)
    @Modifying
    @Query("""
        UPDATE OutboxMessage m
        SET m.status = :claimed, m.updatedAt = :now, m.leaseUntil = :leaseUntil
        WHERE m.id = :id AND m.retryCount = :retryCount
          AND (m.status = :pending OR (m.status = :claimed AND m.leaseUntil <= :now))
        """)
    int claimMessage(@Param("id") Long id, @Param("claimed") EOutboxStatus claimed,
            @Param("pending") EOutboxStatus pending, @Param("retryCount") int retryCount,
            @Param("now") Instant now, @Param("leaseUntil") Instant leaseUntil);

    @Transactional(propagation = Propagation.REQUIRED)
    @Modifying
    @Query("""
        UPDATE OutboxMessage m
        SET m.status = :status, m.retryCount = :retryCount, m.processedAt = :processedAt,
            m.errorMessage = :errorMessage, m.updatedAt = :now, m.leaseUntil = NULL
        WHERE m.id = :id AND m.status = :claimed
          AND m.leaseUntil = :leaseUntil AND m.leaseUntil > :now
        """)
    int finishAttempt(@Param("id") Long id, @Param("claimed") EOutboxStatus claimed,
            @Param("leaseUntil") Instant leaseUntil, @Param("status") EOutboxStatus status,
            @Param("retryCount") int retryCount, @Param("processedAt") Instant processedAt,
            @Param("errorMessage") String errorMessage, @Param("now") Instant now);
}
