package com.wassim.commerce.order.payment;

import com.wassim.commerce.order.payment.service.PaymentReconciliationClaims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import javax.sql.DataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.time.Duration;
import java.util.HashSet;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PaymentReconciliationClaims.class)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class PaymentReconciliationClaimsTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");
    @Autowired
    private DataSource dataSource;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private PaymentReconciliationClaims claims;

    @DynamicPropertySource
    static void databaseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeEach
    void setup() {
        jdbc.setQueryTimeout(5);
        jdbc.execute("TRUNCATE payments, orders, users CASCADE");
        jdbc.execute("INSERT INTO users (id, email, password) VALUES (1, 'claims@example.com', 'hash')");
        jdbc.execute("""
                INSERT INTO orders (id, order_number, order_date, order_status, total_price, total_quantity, user_id)
                SELECT n, 'claims-' || n, CURRENT_TIMESTAMP, 'PENDING_PAYMENT', 10, 1, 1
                FROM generate_series(1, 4) n
                """);
        jdbc.execute("""
                INSERT INTO payments (id, order_id, amount, currency, status, created_at, updated_at)
                SELECT n, n, 10, 'MAD', 'PENDING', CURRENT_TIMESTAMP - INTERVAL '2 minutes', CURRENT_TIMESTAMP
                FROM generate_series(1, 4) n
                """);
    }

    @Test
    void concurrentInstancesClaimDisjointBatches() throws Exception {
        // Separate threads use separate transactions and database connections through the same proxy.
        var executor = Executors.newFixedThreadPool(2);
        var barrier = new CyclicBarrier(2);
        try {
            var first = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return claims.claim(Duration.ofMinutes(1), Duration.ofMinutes(5), 2);
            });
            var second = executor.submit(() -> {
                barrier.await(5, TimeUnit.SECONDS);
                return claims.claim(Duration.ofMinutes(1), Duration.ofMinutes(5), 2);
            });
            var a = first.get(10, TimeUnit.SECONDS);
            var b = second.get(10, TimeUnit.SECONDS);
            assertThat(a).hasSize(2);
            assertThat(b).hasSize(2);
            var ids = new HashSet<Long>();
            a.forEach(c -> ids.add(c.paymentId()));
            b.forEach(c -> ids.add(c.paymentId()));
            assertThat(ids).containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void skipsLockedRowsAndFiltersRecentAndTerminalPayments() throws Exception {
        jdbc.update("UPDATE payments SET status = 'SUCCEEDED' WHERE id = 3");
        jdbc.update("UPDATE payments SET created_at = CURRENT_TIMESTAMP WHERE id = 4");
        try (var connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.createStatement()) {
                statement.executeQuery("SELECT id FROM payments WHERE id = 1 FOR UPDATE").close();
                var result = claims.claim(Duration.ofMinutes(1), Duration.ofMinutes(5), 20);
                assertThat(result).extracting(PaymentReconciliationClaims.Claim::paymentId).containsExactly(2L);
            } finally {
                connection.rollback();
            }
        }
    }

    @Test
    void expiredClaimCanBeReclaimedAndOldOwnerCannotReleaseNewClaim() {
        jdbc.update("DELETE FROM payments WHERE id <> 1");
        var oldClaim = claims.claim(Duration.ZERO, Duration.ofMinutes(5), 1).get(0);
        assertThat(claims.isOwned(oldClaim)).isTrue();
        assertThat(claims.claim(Duration.ZERO, Duration.ofMinutes(5), 1)).isEmpty();
        jdbc.update("""
                UPDATE payments SET reconciliation_claim_until = CURRENT_TIMESTAMP - INTERVAL '1 second',
                    reconciliation_next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'
                """);
        var newClaim = claims.claim(Duration.ZERO, Duration.ofMinutes(5), 1).get(0);
        assertThat(claims.isOwned(oldClaim)).isFalse();
        claims.release(oldClaim, Duration.ZERO);
        assertThat(claims.isOwned(newClaim)).isTrue();
        claims.release(newClaim, Duration.ofSeconds(30));
        assertThat(claims.isOwned(newClaim)).isFalse();
        assertThat(claims.claim(Duration.ZERO, Duration.ofMinutes(5), 1)).isEmpty();
        jdbc.update("UPDATE payments SET reconciliation_next_attempt_at = CURRENT_TIMESTAMP - INTERVAL '1 second'");
        assertThat(claims.claim(Duration.ZERO, Duration.ofMinutes(5), 1)).hasSize(1);
    }

    @Test
    void retriesMoveBehindUncheckedPayments() {
        var first = claims.claim(Duration.ZERO, Duration.ofMinutes(5), 1).get(0);
        claims.release(first, Duration.ZERO);
        var next = claims.claim(Duration.ZERO, Duration.ofMinutes(5), 1).get(0);
        assertThat(next.paymentId()).isNotEqualTo(first.paymentId());
    }
}
