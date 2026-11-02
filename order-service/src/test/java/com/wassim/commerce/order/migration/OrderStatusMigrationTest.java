package com.wassim.commerce.order.migration;

import com.wassim.commerce.order.order.enums.EOrderStatus;
import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.jvm.JdbcConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class OrderStatusMigrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Test
    void upgradesLegacyOrdersWithoutChangingCurrentStatuses() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
             var resources = new ClassLoaderResourceAccessor()) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase("db/changelog/db.changelog-master.yaml",
                    resources, database)) {
                // Reproduce the schema deployed before order cancellation was added.
                liquibase.update(18, new Contexts(), new LabelExpression());
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO users (id, email, password) VALUES (1, 'migration@example.com', 'hash')");
                    statement.execute("""
                            INSERT INTO orders (order_number, order_date, order_status,
                                                total_price, total_quantity, user_id)
                            VALUES ('legacy', CURRENT_TIMESTAMP, 'PLACED', 10, 1, 1),
                                   ('new', CURRENT_TIMESTAMP, 'NEW', 10, 1, 1),
                                   ('paid', CURRENT_TIMESTAMP, 'PAID', 10, 1, 1),
                                   ('canceled', CURRENT_TIMESTAMP, 'CANCELED', 10, 1, 1)
                            """);
                }
                connection.commit();
                liquibase.update(new Contexts(), new LabelExpression());
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("SELECT order_number, order_status, version FROM orders")) {
                    int count = 0;
                    while (result.next()) {
                        EOrderStatus expected = switch (result.getString("order_number")) {
                            case "legacy", "paid" -> EOrderStatus.PAID;
                            case "new" -> EOrderStatus.NEW;
                            case "canceled" -> EOrderStatus.CANCELED;
                            default -> throw new AssertionError("Unexpected order");
                        };
                        assertEquals(expected, EOrderStatus.valueOf(result.getString("order_status")));
                        assertEquals(0, result.getInt("version"));
                        assertFalse(result.wasNull());
                        count++;
                    }
                    assertEquals(4, count);
                }
            }
        }
    }
}
