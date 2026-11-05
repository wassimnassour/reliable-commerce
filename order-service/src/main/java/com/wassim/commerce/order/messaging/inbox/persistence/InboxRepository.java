package com.wassim.commerce.order.messaging.inbox.persistence;

import com.wassim.commerce.order.messaging.inbox.domain.InboxMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;

@Repository
public interface InboxRepository extends JpaRepository<InboxMessage, Long> {

    boolean existsByMessageIdAndConsumerName(String messageId, String consumerName);

    Optional<InboxMessage> findByMessageIdAndConsumerName(String messageId, String consumerName);

    @Modifying
    @Query("DELETE FROM InboxMessage i WHERE i.processedAt < :cutoff")
    int deleteProcessedBefore(@Param("cutoff") Instant cutoff);
}
