package com.wassim.commerce.order.order.domain;


import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

@Setter
@Getter
@NoArgsConstructor
@Table(
        name = "order_idempotency",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "user_id_idempotency_key",
                        columnNames = {
                                "user_id",
                                "idempotency_key"
                        }
                )

        }
)
@Entity()
public class OrderIdempotency {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "idempotency_key", nullable = false, length = 255)
    private String idempotencyKey;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "request_payload",
            columnDefinition = "jsonb", nullable = false)
    private String payload;
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String response;

    private Instant createdAt;

    private Instant updatedAt;


    @PrePersist
    public void prePersist() {
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }


    @PreUpdate
    public void preUpdate() {
        this.updatedAt = Instant.now();
    }
}


