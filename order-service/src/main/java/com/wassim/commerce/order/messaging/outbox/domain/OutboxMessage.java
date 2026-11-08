package com.wassim.commerce.order.messaging.outbox.domain;

import com.wassim.commerce.order.messaging.outbox.enums.EOutboxStatus;
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
@Table(name = "outbox_messages")
public class OutboxMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "aggregate_type", nullable = false, length = 50)
    private String aggregateType; // e.g., "ORDER", "PAYMENT"

    @Column(name = "aggregate_id", nullable = false, length = 100)
    private String aggregateId; // e.g., "1024"

    @Column(name = "exchange", nullable = false, length = 100)
    private String exchange;

    @Column(name = "routing_key", nullable = false, length = 100)
    private String routingKey;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "payload", columnDefinition = "jsonb", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private EOutboxStatus status;

    @Column(name = "updated_at" , nullable = false)
    private Instant updatedAt;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "processed_at")
    private Instant processedAt;

    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
    }
    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}