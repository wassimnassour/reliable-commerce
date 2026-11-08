package com.wassim.commerce.order.messaging.outbox.presissetance;

import com.wassim.commerce.order.messaging.outbox.domain.OutboxMessage;
import com.wassim.commerce.order.messaging.outbox.enums.EOutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;


import jakarta.persistence.QueryHint;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxMessage, Long> {

    @Transactional(propagation = Propagation.REQUIRES_NEW)
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


    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @Modifying
    @Query(
            """
        update OutboxMessage  m set m.status=:claimedStatus, m.updatedAt=:now  where m.id =:messageId and m.status=:pendingStatus
       """
    )
    int claimMessage(
            @Param("messageId") Long messageId,
            @Param("claimedStatus") EOutboxStatus claimedStatus,
            @Param("pendingStatus") EOutboxStatus pendingStatus,
            @Param("now") Instant now
    );
}