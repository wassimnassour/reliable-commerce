package com.wassim.commerce.order.order.service;

import com.wassim.commerce.order.TestcontainersConfiguration;
import com.wassim.commerce.order.exaptions.BadRequest;
import com.wassim.commerce.order.order.dto.request.CreateRequestDto;
import com.wassim.commerce.order.order.records.OrderRequest;
import com.wassim.commerce.order.product.domain.Product;
import com.wassim.commerce.order.product.persistence.ProductRepository;
import com.wassim.commerce.order.user.domain.User;
import com.wassim.commerce.order.user.presistance.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.*;

// Deliberately no test transaction: each service invocation must commit or roll
// back itself, including invocations made by concurrent checkout threads.
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class OrderServiceTest {
    @Autowired OrderService service;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired org.springframework.transaction.PlatformTransactionManager transactions;
    @Autowired com.wassim.commerce.order.order.presisstance.OrderIdempotencyRepository keys;

    Long userId;
    final List<Long> productIds = new ArrayList<>();

    @BeforeEach
    void createUser() {
        User user = new User();
        user.setEmail(UUID.randomUUID() + "@example.com");
        user.setPassword("test-password-hash");
        userId = users.saveAndFlush(user).getId();
    }

    @AfterEach
    void cleanOwnedFixtures() {
        jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM orders WHERE user_id = ?", userId);
        for (Long id : productIds) jdbc.update("DELETE FROM products WHERE id = ?", id);
        jdbc.update("DELETE FROM order_idempotency WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }

    Product product(int stock) {
        Product product = new Product();
        product.setProductName("Stock test product");
        product.setProductPrice(new BigDecimal("19.99"));
        product.setProductQuantity(stock);
        product.setStatus(true);
        product = products.saveAndFlush(product);
        productIds.add(product.getId());
        return product;
    }

    OrderRequest item(Product product, int quantity) {
        return new OrderRequest(product.getId(), quantity, "123 Test Street");
    }

    CreateRequestDto request(OrderRequest... items) {
        return new CreateRequestDto(List.of(items));
    }

    int stock(Product product) {
        return products.findById(product.getId()).orElseThrow().getProductQuantity();
    }

    long orderCount() {
        return jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Long.class, userId);
    }

    long itemCount() {
        return jdbc.queryForObject("""
                SELECT count(*) FROM order_items i JOIN orders o ON o.id = i.order_id
                WHERE o.user_id = ?
                """, Long.class, userId);
    }

    @Test
    void retryReturnsTheSameOrderWithoutDecreasingStockAgain() {
        Product product = product(3);
        var request = request(item(product, 3));
        var first = service.createOrder("retry-key", userId, request);
        var retry = service.createOrder("retry-key", userId, request);
        assertThat(retry).isEqualTo(first);
        assertThat(first.totalAmount()).isEqualByComparingTo("59.97");
        assertThat(stock(product)).isZero();
        assertThatThrownBy(() -> service.createOrder("retry-key", userId, request(item(product, 1))))
                .isInstanceOf(com.wassim.commerce.order.exaptions.IdempotencyConflictException.class);
        assertThat(orderCount()).isEqualTo(1);
        assertThat(itemCount()).isEqualTo(1);
    }

    @Test
    void failedOrderRollsBackItsKeyAndCanBeRetried() {
        Product first = product(10);
        Product second = product(1);
        var request = request(item(first, 2), item(second, 2));
        assertThatThrownBy(() -> service.createOrder("failed-key", userId, request))
                .isInstanceOf(BadRequest.class);
        assertThat(keys.findByIdempotencyKeyAndUserId("failed-key", userId)).isNull();
        assertThat(stock(first)).isEqualTo(10);
        assertThat(orderCount()).isZero();
        second.setProductQuantity(5);
        products.saveAndFlush(second);
        var result = service.createOrder("failed-key", userId, request);
        assertThat(result.orderId()).isNotNull();
        assertThat(stock(first)).isEqualTo(8);
        assertThat(stock(second)).isEqualTo(3);
        assertThat(orderCount()).isEqualTo(1);
    }

    @Test
    void competingOrdersCannotOversellStock() throws Exception {
        Product product = product(10);
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var checkout = (java.util.concurrent.Callable<Object>) () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return service.createOrder(java.util.UUID.randomUUID().toString(), userId, request(item(product, 6)));
            };
            List<Future<Object>> attempts = List.of(executor.submit(checkout), executor.submit(checkout));
            int successes = 0;
            int rejected = 0;
            for (var attempt : attempts) {
                try {
                    assertThat(attempt.get(20, TimeUnit.SECONDS)).isNotNull();
                    successes++;
                } catch (ExecutionException ex) {
                    assertThat(ex.getCause()).isInstanceOf(BadRequest.class)
                            .hasMessageContaining("Insufficient stock");
                    rejected++;
                }
            }
            assertThat(successes).isEqualTo(1);
            assertThat(rejected).isEqualTo(1);
            assertThat(stock(product)).isEqualTo(4);
            assertThat(orderCount()).isEqualTo(1);
            assertThat(itemCount()).isEqualTo(1);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void concurrentRetriesCreateOneOrderAndReturnTheSameResponse() throws Exception {
        Product product = product(10);
        var request = request(item(product, 3));
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var checkout = (java.util.concurrent.Callable<com.wassim.commerce.order.order.dto.response.CreateOrderResponse>) () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return service.createOrder("concurrent-key", userId, request);
            };
            var first = executor.submit(checkout);
            var second = executor.submit(checkout);
            assertThat(first.get(20, TimeUnit.SECONDS)).isEqualTo(second.get(20, TimeUnit.SECONDS));
            assertThat(orderCount()).isEqualTo(1);
            assertThat(itemCount()).isEqualTo(1);
            assertThat(stock(product)).isEqualTo(7);
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void blockedKeyTimesOutRollsBackAndCanBeRetried() throws Exception {
        Product product = product(10);
        var request = request(item(product, 3));
        var locked = new java.util.concurrent.CountDownLatch(1);
        var release = new java.util.concurrent.CountDownLatch(1);
        var executor = Executors.newSingleThreadExecutor();
        var holder = executor.submit(() -> new org.springframework.transaction.support.TransactionTemplate(transactions)
                .executeWithoutResult(status -> {
                    status.setRollbackOnly();
                    keys.tryClaim(userId, "locked-key", "{}");
                    locked.countDown();
                    try {
                        if (!release.await(15, TimeUnit.SECONDS)) throw new AssertionError("Lock holder timed out");
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        throw new RuntimeException(ex);
                    }
                }));
        try {
            assertThat(locked.await(10, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> service.createOrder("locked-key", userId, request))
                    .isInstanceOf(org.springframework.dao.PessimisticLockingFailureException.class);
            assertThat(stock(product)).isEqualTo(10);
            assertThat(orderCount()).isZero();
        } finally {
            release.countDown();
            try {
                holder.get(10, TimeUnit.SECONDS);
            } finally {
                executor.shutdownNow();
                assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
            }
        }
        assertThat(keys.findByIdempotencyKeyAndUserId("locked-key", userId)).isNull();
        assertThat(service.createOrder("locked-key", userId, request).orderId()).isNotNull();
        assertThat(stock(product)).isEqualTo(7);
        assertThat(orderCount()).isEqualTo(1);
    }

}
