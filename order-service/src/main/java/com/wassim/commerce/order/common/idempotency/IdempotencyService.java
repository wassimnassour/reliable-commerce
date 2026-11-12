package com.wassim.commerce.order.common.idempotency;

import com.wassim.commerce.order.common.idempotency.domain.IdempotencyEntity;
import com.wassim.commerce.order.exaptions.IdempotencyConflictException;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
public class IdempotencyService {
    private final IdempotencyRepository repository;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Transactional
    public <T> T execute(Long userId, String operation, String key, Object request,
                         Class<T> responseType, Supplier<T> action) {
        entityManager.createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate();

        String payload = objectMapper.writeValueAsString(request);
        boolean claimed = repository.tryClaim(userId, operation, key, payload) == 1;
        IdempotencyEntity record = repository.findByUserIdAndOperationAndIdempotencyKey(userId, operation, key);

        if (!claimed) {
            JsonNode savedPayload = objectMapper.readTree(record.getPayload());
            if (!savedPayload.equals(objectMapper.readTree(payload))) {
                throw new IdempotencyConflictException();
            }
            if (record.getResponse() == null) {
                throw new IllegalStateException("Idempotency record has no saved response");
            }
            return objectMapper.readValue(record.getResponse(), responseType);
        }

        T response = action.get();
        record.setResponse(objectMapper.writeValueAsString(response));
        return response;
    }
}
