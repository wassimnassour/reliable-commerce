
package com.wassim.commerce.order.messaging;

import com.wassim.commerce.order.TestcontainersConfiguration;
import com.wassim.commerce.order.fixtures.GenerateProducts;
import com.wassim.commerce.order.fixtures.GenerateUser;
import com.wassim.commerce.order.messaging.outbox.OutboxPoller;
import com.wassim.commerce.order.messaging.outbox.presissetance.OutboxRepository;
import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.dto.response.CreateOrderResponse;
import com.wassim.commerce.order.order.enums.EOrderStatus;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.order.records.OrderRequest;
import com.wassim.commerce.order.order.service.OrderService;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.UserRepository;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = {
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "spring.rabbitmq.listener.direct.auto-startup=false",
        "spring.rabbitmq.dynamic=false"
})
class CancelOrderTest {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private OutboxRepository outboxRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private OutboxPoller outboxPoller;

    @Test
    void cancelOrder_transactionFails_rollsBackOrderAndOutbox() {


        Product product = productRepository.saveAndFlush(
                GenerateProducts.generateGoodProduct()
        );

        User user = userRepository.saveAndFlush(
                GenerateUser.generateUser()
        );


        OrderRequest orderRequest = new OrderRequest(
                product.getId(),
                1,
                "333"
        );

        CreateRequestDto request = new CreateRequestDto(
                List.of(orderRequest)
        );

        CreateOrderResponse createdOrder = orderService.createOrder(
                UUID.randomUUID().toString(),
                user.getId(),
                request
        );

        Long orderId = createdOrder.orderId();


        int stockBefore = productRepository
                .findById(product.getId())
                .orElseThrow()
                .getProductQuantity();

        EOrderStatus orderStatusBefore = orderRepository
                .findById(orderId)
                .orElseThrow()
                .getOrderStatus();

        long outboxCountBefore = outboxRepository.count();

        assertThat(stockBefore).isEqualTo(99);


        assertThatThrownBy(() ->
                transactionTemplate.executeWithoutResult(status -> {

                    orderService.cancelOrder(user.getId(), orderId);

                    assertThat(
                            orderRepository.findById(orderId)
                                    .orElseThrow()
                                    .getOrderStatus()
                    ).isEqualTo(EOrderStatus.CANCELED);

                    assertThat(
                            productRepository.findById(product.getId())
                                    .orElseThrow()
                                    .getProductQuantity()
                    ).isEqualTo(stockBefore + 1);

                    assertThat(outboxRepository.count())
                            .isEqualTo(outboxCountBefore + 1);

                    throw new IllegalStateException("Simulated failure");
                })
        )
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Simulated failure");


        assertThat(
                orderRepository.findById(orderId)
                        .orElseThrow()
                        .getOrderStatus()
        ).isEqualTo(orderStatusBefore);

        assertThat(
                productRepository.findById(product.getId())
                        .orElseThrow()
                        .getProductQuantity()
        ).isEqualTo(stockBefore);

        assertThat(outboxRepository.count())
                .isEqualTo(outboxCountBefore);
    }
}
