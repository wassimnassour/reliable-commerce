package com.wassim.commerce.order.order.service;

import com.wassim.commerce.order.TestcontainersConfiguration;
import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.fixtures.GenerateProducts;
import com.wassim.commerce.order.fixtures.GenerateRoles;
import com.wassim.commerce.order.fixtures.GenerateUser;
import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.dto.response.CreateOrderResponse;
import com.wassim.commerce.order.order.presisstance.OrderRepository;
import com.wassim.commerce.order.order.records.OrderRequest;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.user.domain.Role;
import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.RoleRepository;
import com.wassim.commerce.order.user.presistance.UserRepository;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import org.springframework.test.context.transaction.TestTransaction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.concurrent.*;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
public class OrderServiceTest {

        @Autowired
        private OrderRepository orderRepository;
        @Autowired
        private ProductRepository productRepository;

        @Autowired
        RoleRepository roleRepository;

        @Autowired
        private OrderService orderService;
        @Autowired
        UserRepository userRepository;
        @Autowired
        private TestcontainersConfiguration testcontainersConfiguration;

        @Autowired
        private org.springframework.jdbc.core.JdbcTemplate jdbc;

        @Test
        void rollsBackFirstProductWhenSecondProductHasInsufficientStock() {
                User user = GenerateUser.generateUser();
                user.setEmail(java.util.UUID.randomUUID() + "@example.com");
                user = userRepository.saveAndFlush(user);
                Long userId = user.getId();

                Product first = GenerateProducts.generateGoodProduct();
                Product second = GenerateProducts.generateGoodProduct();
                first.setProductQuantity(10);
                second.setProductQuantity(1);
                productRepository.saveAndFlush(first);
                productRepository.saveAndFlush(second);

                try {
                        var request = new CreateRequestDto(List.of(
                                        new OrderRequest(first.getId(), 3, "Address"),
                                        new OrderRequest(second.getId(), 2, "Address")));

                        assertThatThrownBy(() -> orderService.createOrder(java.util.UUID.randomUUID().toString(), userId, request))
                                        .isInstanceOf(BadRequest.class)
                                        .hasMessageContaining("Insufficient stock");

                        assertThat(productRepository.findById(first.getId()).orElseThrow().getProductQuantity())
                                        .isEqualTo(10);
                        assertThat(productRepository.findById(second.getId()).orElseThrow().getProductQuantity())
                                        .isEqualTo(1);
                        assertThat(jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?",
                                        Long.class, userId)).isZero();
                        assertThat(jdbc.queryForObject(
                                        "SELECT count(*) FROM order_items WHERE product_id IN (?, ?)",
                                        Long.class, first.getId(), second.getId())).isZero();
                } finally {
                        jdbc.update("DELETE FROM order_items WHERE product_id IN (?, ?)", first.getId(), second.getId());
                        jdbc.update("DELETE FROM orders WHERE user_id = ?", userId);
                        productRepository.deleteById(first.getId());
                        productRepository.deleteById(second.getId());
                        jdbc.update("DELETE FROM order_idempotency WHERE user_id = ?", userId);
                        userRepository.deleteById(userId);
                }
        }

        @Transactional
        @Test
        public void TestCreateOrderWithGoodInputs() {
                User generateUser = GenerateUser.generateUser();
                com.wassim.commerce.order.product.domain.Product product = GenerateProducts.generateGoodProduct();
                List<Role> roles = roleRepository.findAll();
                generateUser.setRoles(roles);
                roleRepository.saveAll(roles);

                userRepository.saveAndFlush(generateUser);
                Product newProduct = productRepository.saveAndFlush(product);
                userRepository.findAll();

                int quantity = 3;

                CreateRequestDto requestDto = new CreateRequestDto(
                                List.of(
                                                new OrderRequest(
                                                                newProduct.getId(),
                                                                quantity,
                                                                "Adress"))

                );

                CreateOrderResponse response = orderService.createOrder(java.util.UUID.randomUUID().toString(), generateUser.getId(), requestDto);

                Assertions.assertNotNull(response);
                assertThat(response).isNotNull();
                assertThat(response.orderId()).isNotNull();
                assertThat(response.totalAmount())
                                .isEqualTo(newProduct.getProductPrice().multiply(BigDecimal.valueOf(quantity)));
                assertThat(response.totalQuantity()).isEqualTo(quantity);
                Product updatedProduct = productRepository
                                .findById(product.getId())
                                .orElseThrow();

                assertThat(updatedProduct.getProductQuantity())
                                .isEqualTo(97);

                assertThat(orderRepository.existsById(response.orderId()))
                                .isTrue();
        }

        @Transactional
        @Test
        public void TestCreateOrderWithStatusNotActive() {
                User generateUser = GenerateUser.generateUser();
                Product product = GenerateProducts.generateProductStatusDisabled();
                Product newProduct = productRepository.saveAndFlush(product);
                userRepository.saveAndFlush(generateUser);

                int quantity = 3;
                CreateRequestDto requestDto = new CreateRequestDto(
                                List.of(
                                                new OrderRequest(
                                                                newProduct.getId(),
                                                                quantity,
                                                                "Adress"))

                );

                Runnable createOrder = () -> orderService.createOrder(java.util.UUID.randomUUID().toString(), generateUser.getId(), requestDto);
                assertThatThrownBy(createOrder::run).isInstanceOf(BadRequest.class)
                                .hasMessage("Product  is not available" + newProduct.getId());
        }

        @Transactional
        @Test
        public void TestRunningCreateOrderConcurrentWithLimitedQuantity() {
                User generateUser = GenerateUser.generateUser();
                Product product = GenerateProducts.generateGoodProduct();
                List<Role> roles = roleRepository.findAll();
                generateUser.setRoles(roles);
                roleRepository.saveAll(roles);
                User user = userRepository.saveAndFlush(generateUser);
                Product newProduct = productRepository.saveAndFlush(product);
                userRepository.findAll();

                TestTransaction.flagForCommit();
                TestTransaction.end();

                var barrier = new CyclicBarrier(2);

                int quantityFirstCheckout = 50;
                int quantitySecondCheckout = 49;

                Callable<CreateOrderResponse> checkout1 = () -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        return orderService.createOrder(java.util.UUID.randomUUID().toString(),
                                        user.getId(),
                                        new CreateRequestDto(
                                                        List.of(
                                                                        new OrderRequest(
                                                                                        newProduct.getId(),
                                                                                        quantityFirstCheckout,
                                                                                        "Adress"))));
                };

                Callable<CreateOrderResponse> checkout2 = () -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        return orderService.createOrder(java.util.UUID.randomUUID().toString(),
                                        user.getId(),
                                        new CreateRequestDto(
                                                        List.of(
                                                                        new OrderRequest(
                                                                                        newProduct.getId(),
                                                                                        quantitySecondCheckout,
                                                                                        "Adress"))));
                };

                ExecutorService executor = Executors.newFixedThreadPool(2);
                try {
                        Future<CreateOrderResponse> first = executor.submit(checkout1);
                        Future<CreateOrderResponse> second = executor.submit(checkout2);

                        CreateOrderResponse firstResponse = first.get();
                        CreateOrderResponse secondResponse = second.get();
                        assertNotEquals(firstResponse.orderId(), secondResponse.orderId());

                        assertTrue(orderRepository.existsById(firstResponse.orderId()));
                        assertTrue(orderRepository.existsById(secondResponse.orderId()));
                        assertTrue(firstResponse.totalQuantity().equals(quantityFirstCheckout));
                        assertTrue(secondResponse.totalQuantity().equals(quantitySecondCheckout));

                } catch (Exception e) {
                        throw new RuntimeException(e);
                }

        }

        @Transactional
        @Test
        public void TestRunningCreateOrderConcurrentWithLimitedQuantity2() {
                User generateUser = GenerateUser.generateUser();
                Product product = GenerateProducts.generateGoodProduct();
                List<Role> roles = roleRepository.findAll();
                generateUser.setRoles(roles);
                roleRepository.saveAll(roles);
                User user = userRepository.saveAndFlush(generateUser);
                Product newProduct = productRepository.saveAndFlush(product);
                TestTransaction.flagForCommit();
                TestTransaction.end();
                var barrier = new CyclicBarrier(2);

                int quantityFirstCheckout = 66;
                int quantitySecondCheckout = 40;

                ExecutorService executor = Executors.newFixedThreadPool(2);

                Callable<CreateOrderResponse> checkout1 = () -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        return orderService.createOrder(java.util.UUID.randomUUID().toString(),
                                        user.getId(),
                                        new CreateRequestDto(
                                                        List.of(
                                                                        new OrderRequest(
                                                                                        newProduct.getId(),
                                                                                        quantityFirstCheckout,
                                                                                        "Adress"))));
                };

                Callable<CreateOrderResponse> checkout2 = () -> {
                        barrier.await(5, TimeUnit.SECONDS);
                        return orderService.createOrder(java.util.UUID.randomUUID().toString(),
                                        user.getId(),
                                        new CreateRequestDto(
                                                        List.of(
                                                                        new OrderRequest(
                                                                                        newProduct.getId(),
                                                                                        quantitySecondCheckout,
                                                                                        "Adress"))));
                };

                try {

                        Future<CreateOrderResponse> future1 = executor.submit(checkout1);
                        Future<CreateOrderResponse> future2 = executor.submit(checkout2);
                        CreateOrderResponse firstResponse = future1.get();
                        CreateOrderResponse secondResponse = future2.get();

                        assertNotEquals(firstResponse.orderId(), secondResponse.orderId());
                        assertTrue(orderRepository.existsById(firstResponse.orderId()));

                } catch (Exception e) {
                        assertThat(e.getCause())
                                        .isInstanceOf(RuntimeException.class);
                }

        }
}
