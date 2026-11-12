package com.wassim.commerce.order.common.idempotency;

import com.wassim.commerce.order.common.idempotency.domain.IdempotencyEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface IdempotencyRepository extends JpaRepository<IdempotencyEntity, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO order_idempotency (
                user_id, operation, idempotency_key, request_payload,
                created_at, updated_at
            )
            VALUES (
                :userId, :operation, :key, CAST(:payload AS jsonb),
                CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
            )
            ON CONFLICT (user_id, operation, idempotency_key) DO NOTHING
            """, nativeQuery = true)
    int tryClaim(
            @Param("userId") Long userId,
            @Param("operation") String operation,
            @Param("key") String key,
            @Param("payload") String payload);

    IdempotencyEntity findByUserIdAndOperationAndIdempotencyKey(Long userId, String operation, String idempotencyKey);
}
