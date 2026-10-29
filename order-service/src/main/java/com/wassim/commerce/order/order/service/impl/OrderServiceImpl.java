package com.wassim.commerce.order.order.service.impl;

import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.exaptions.IdempotencyConflictException;
import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.order.domain.OrderIdempotency;
import com.wassim.commerce.order.order.domain.OrderItems;
import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.dto.response.CreateOrderResponse;
import com.wassim.commerce.order.order.mapper.OrderMapper;
import com.wassim.commerce.order.order.presisstance.OrderIdempotencyRepository;
import com.wassim.commerce.order.order.presisstance.OrderItemsRepository;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.order.records.OrderRequest;
import com.wassim.commerce.order.order.service.OrderService;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import java.util.stream.Collectors;

@RequiredArgsConstructor
@Service
public class OrderServiceImpl implements OrderService {
    private final ProductRepository productRepository;
    private final OrderItemsRepository orderItemsRepository;
    private final UserRepository userRepository;
    private final OrderRepository orderRepository;
    private final OrderMapper orderMapper;
    private final OrderIdempotencyRepository orderIdempotencyRepository;
    private final ObjectMapper objectMapper;

    private final EntityManager entityManager;

    @Transactional
    public CreateOrderResponse createOrder(String orderIdempotencyKey, Long userId, CreateRequestDto createRequestDto) {
        entityManager
                .createNativeQuery("SET LOCAL lock_timeout = '3s'").executeUpdate();

        String payload = writeJson(createRequestDto);
        int insertedIntoIdempotency = orderIdempotencyRepository.tryClaim(userId, orderIdempotencyKey, payload);

        OrderIdempotency idempotency = orderIdempotencyRepository.findByIdempotencyKeyAndUserId(orderIdempotencyKey,
                userId);
        if (insertedIntoIdempotency == 0) {
            JsonNode savedPayload = readJson(idempotency.getPayload(), JsonNode.class);
            JsonNode currentPayload = readJson(payload, JsonNode.class);

            if (!savedPayload.equals(currentPayload)) {
                throw new IdempotencyConflictException();
            }

            if (idempotency.getResponse() == null) {
                throw new IllegalStateException(
                        "Idempotency record has no saved response");
            }

            return readJson(idempotency.getResponse(), CreateOrderResponse.class);

        }

        boolean hasDuplicates = createRequestDto.products().stream().collect(
                Collectors.groupingBy(OrderRequest::productId, Collectors.counting())).values().stream()
                .anyMatch(count -> count > 1);

        if (hasDuplicates) {
            throw new BadRequest("Duplicate product ids found");
        }

        User user = userRepository.findById(userId).orElseThrow(
                () -> new BadRequest("User not found"));

        List<OrderItems> orderItemsList = new ArrayList<>();
        BigDecimal totalAmount = BigDecimal.ZERO;

        Integer totalQuantity = 0;

        Order order = new Order();
        order.setUser(user);

        for (OrderRequest pr : createRequestDto.products()) {
            Product product = productRepository.findById(pr.productId())
                    .orElseThrow(() -> new BadRequest("Product with id " + pr.productId() + " is not found"));

            BigDecimal totalAmountOfOneProduct = product.getProductPrice().multiply(BigDecimal.valueOf(pr.quantity()));
            if (!Boolean.TRUE.equals(product.getStatus())) {
                throw new BadRequest("Product  is not available" + pr.productId());
            }

            if (pr.quantity() > product.getProductQuantity()) {
                throw new BadRequest("Insufficient stock for this product" + pr.productId());
            }

            int affectedProductRows = productRepository.decreaseTheStock(pr.quantity(), product.getId());
            if (affectedProductRows != 1) {
                throw new BadRequest("Insufficient stock for this product --- " + pr.productId());
            }

            OrderItems orderItems = new OrderItems();
            orderItems.setProduct(product);
            orderItems.setQuantity(pr.quantity());
            orderItems.setTotalPrice(totalAmountOfOneProduct);
            orderItems.setOrder(order);
            orderItemsList.add(orderItems);
            totalAmount = totalAmount.add(totalAmountOfOneProduct);
            totalQuantity = totalQuantity + pr.quantity();
        }

        order.setOrderDate(Instant.now());
        order.setTotalPrice(totalAmount);
        order.setTotalQuantity(totalQuantity);
        order.setOrderItems(orderItemsList);

        orderRepository.save(order);

        orderItemsRepository.saveAll(orderItemsList);

        CreateOrderResponse response = orderMapper.toCreateOrderResponse(order);

        idempotency.setResponse(writeJson(response));
        orderIdempotencyRepository.save(idempotency);

        return response;

    }

    private String writeJson(Object value) {
        return objectMapper.writeValueAsString(value);
    }

    private <T> T readJson(String json, Class<T> type) {
        return objectMapper.readValue(json, type);
    }
}
