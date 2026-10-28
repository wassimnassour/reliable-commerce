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
class OrderStockIntegrationTest {
    @Autowired OrderService service;
    @Autowired ProductRepository products;
    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;

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
    void sufficientStockCreatesOrderAndDecreasesStock() {
        Product product = product(10);
        var response = service.createOrder(userId, request(item(product, 3)));
        assertThat(response.orderId()).isNotNull();
        assertThat(response.totalQuantity()).isEqualTo(3);
        assertThat(response.totalAmount()).isEqualByComparingTo("59.97");
        assertThat(stock(product)).isEqualTo(7);
        assertThat(orderCount()).isEqualTo(1);
        assertThat(itemCount()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT quantity FROM order_items WHERE order_id = ?",
                Integer.class, response.orderId())).isEqualTo(3);
    }

    @Test
    void insufficientStockRejectsOrderWithoutChangingDatabase() {
        Product product = product(2);
        assertThatThrownBy(() -> service.createOrder(userId, request(item(product, 3))))
                .isInstanceOf(BadRequest.class).hasMessageContaining("Insufficient stock");
        assertThat(stock(product)).isEqualTo(2);
        assertThat(orderCount()).isZero();
        assertThat(itemCount()).isZero();
    }

    @Test
    void zeroStockRejectsOrder() {
        Product product = product(0);
        assertThatThrownBy(() -> service.createOrder(userId, request(item(product, 1))))
                .isInstanceOf(BadRequest.class).hasMessageContaining("Insufficient stock");
        assertThat(stock(product)).isZero();
        assertThat(orderCount()).isZero();
    }

    @Test
    void buyingExactlyRemainingStockSucceeds() {
        Product product = product(3);
        var response = service.createOrder(userId, request(item(product, 3)));
        assertThat(response.totalQuantity()).isEqualTo(3);
        assertThat(stock(product)).isZero();
        assertThat(orderCount()).isEqualTo(1);
        assertThat(itemCount()).isEqualTo(1);
    }

    @Test
    void laterInsufficientProductRollsBackEarlierAtomicStockDecrease() {
        Product first = product(10);
        Product second = product(1);
        assertThatThrownBy(() -> service.createOrder(userId, request(item(first, 3), item(second, 2))))
                .isInstanceOf(BadRequest.class).hasMessageContaining("Insufficient stock");
        assertThat(stock(first)).isEqualTo(10);
        assertThat(stock(second)).isEqualTo(1);
        assertThat(orderCount()).isZero();
        assertThat(itemCount()).isZero();
    }

    @Test
    void competingOrdersCannotOversellStock() throws Exception {
        Product product = product(10);
        var barrier = new CyclicBarrier(2);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var checkout = (java.util.concurrent.Callable<Object>) () -> {
                barrier.await(10, TimeUnit.SECONDS);
                return service.createOrder(userId, request(item(product, 6)));
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
    public void TestQuantityDontMatchInOneProduct() throws Exception {

    }
}
