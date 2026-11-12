package com.wassim.commerce.order.order.service.impl;

import com.wassim.commerce.order.common.idempotency.IdempotencyService;
import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.messaging.config.MessagingDestinations;
import com.wassim.commerce.order.messaging.outbox.events.OrderCancelEvent;
import com.wassim.commerce.order.messaging.outbox.events.OrderCreatedEvent;
import com.wassim.commerce.order.messaging.outbox.records.PublishRequestRecord;
import com.wassim.commerce.order.messaging.outbox.service.OutboxService;
import com.wassim.commerce.order.order.domain.Order;
import com.wassim.commerce.order.order.domain.OrderItems;
import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.dto.response.CancelOrderResponse;
import com.wassim.commerce.order.order.dto.response.CreateOrderResponse;
import com.wassim.commerce.order.order.enums.EOrderStatus;
import com.wassim.commerce.order.order.mapper.OrderMapper;
import com.wassim.commerce.order.order.presisstance.OrderItemsRepository;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.order.records.OrderRequest;
import com.wassim.commerce.order.order.service.OrderService;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.UserRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final IdempotencyService idempotencyService;
    private final MessagingDestinations messagingDestinations;
    private final OutboxService outboxService;

    @Transactional
    @Override
    public CreateOrderResponse createOrder(String orderIdempotencyKey, Long userId, CreateRequestDto createRequestDto) {
        return idempotencyService.execute(userId, "POST /orders", orderIdempotencyKey, createRequestDto,
                CreateOrderResponse.class, () -> createOrder(userId, createRequestDto));
    }

    private CreateOrderResponse createOrder(Long userId, CreateRequestDto createRequestDto) {
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

        outboxService.publish(PublishRequestRecord.builder().aggregateType("orders").routingKey( messagingDestinations.orderCreatedRoutingKey()).payload(new OrderCreatedEvent(order.getId())).exchange(messagingDestinations.ordersExchange()).aggregateId(String.valueOf(order.getId())).build());
        return response;

    }

    @Transactional
    @Override
    public CancelOrderResponse cancelOrder(Long userId, Long orderId) {


        Order order = orderRepository.findById(orderId).orElseThrow(
                ()-> new BadRequest("This order does not exist ")
        );

        if(!order.getUser().getId().equals(userId)) {
            throw new BadRequest("Unauthorized access ");
        }

        if(order.getOrderStatus() == EOrderStatus.CANCELED){
            return  orderMapper.toCancelOrderResponse(order);
        }

        order.setOrderStatus(EOrderStatus.CANCELED);

        order.getOrderItems().forEach(orderItem -> {

            Product product  = productRepository.findById(orderItem.getProduct().getId()).orElseThrow(
                    ()->  new BadRequest("Product with id " + orderItem.getProduct().getId() + " is not found")
            );
            product.setProductQuantity(product.getProductQuantity() + orderItem.getQuantity());
            productRepository.save(product);
        });

        orderRepository.save(order);
        outboxService.publish(
                 PublishRequestRecord.builder().aggregateType("orders").aggregateId(String.valueOf( order.getId())).payload(new OrderCancelEvent( orderId,userId)).routingKey(  messagingDestinations.routingKey("orders","order.cancelled"))
                         .exchange( messagingDestinations.exchange("orders")).build()
        );
        return orderMapper.toCancelOrderResponse(order);
    }
}
