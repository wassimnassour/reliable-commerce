package com.wassim.commerce.order.messaging.inbox.domain;

import com.wassim.commerce.order.messaging.inbox.enums.EInboxStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "inbox_messages", uniqueConstraints = {
        @UniqueConstraint(name = "uq_inbox_messages_message_consumer", columnNames = {"message_id", "consumer_name"})
})
public class InboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "message_id", nullable = false, length = 100)
    private String messageId;

    @Column(name = "consumer_name", nullable = false, length = 100)
    private String consumerName;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EInboxStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @PrePersist
    public void prePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.processedAt == null) {
            this.processedAt = Instant.now();
        }
        if (this.status == null) {
            this.status = EInboxStatus.PROCESSED;
        }
    }
}
