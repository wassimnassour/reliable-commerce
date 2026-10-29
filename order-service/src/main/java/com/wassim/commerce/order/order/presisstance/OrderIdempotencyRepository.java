package com.wassim.commerce.order.order.presisstance;

import com.wassim.commerce.order.order.domain.OrderIdempotency;
import lombok.val;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import tools.jackson.databind.JsonNode;

import java.util.List;

@Repository
public interface OrderIdempotencyRepository extends JpaRepository<OrderIdempotency, Long> {

     @Modifying
     @Query(value = """
    INSERT INTO order_idempotency (
        user_id, idempotency_key, request_payload,
        created_at, updated_at
    )
    VALUES (
        :userId, :key, CAST(:payload AS jsonb),
        CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
    )
    ON CONFLICT (user_id, idempotency_key) DO NOTHING
    """, nativeQuery = true)
     int  tryClaim(
             @Param("userId") Long userId,
             @Param("key") String key,
             @Param("payload") String payload
     );
     OrderIdempotency findByIdempotencyKeyAndUserId(String idempotencyKey , Long userId);
}
