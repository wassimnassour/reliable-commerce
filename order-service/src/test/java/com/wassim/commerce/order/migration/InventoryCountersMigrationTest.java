package com.wassim.commerce.order.migration;

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
class InventoryCountersMigrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Test
    void preservesAvailableStockAndBackfillsOnlyActiveReservations() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
             var resources = new ClassLoaderResourceAccessor()) {
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase("db/changelog/db.changelog-master.yaml", resources, database)) {
                int precedingChanges = 0;
                for (var change : liquibase.getDatabaseChangeLog().getChangeSets()) {
                    if (change.getId().equals("030-inventory-stock-counters")) {
                        break;
                    }
                    precedingChanges++;
                }
                liquibase.update(precedingChanges, new Contexts(), new LabelExpression());
                try (var statement = connection.createStatement()) {
                    statement.execute("INSERT INTO users (id, email, password) VALUES (1, 'stock@example.com', 'hash')");
                    statement.execute("INSERT INTO products (id, product_price) VALUES (1, 10), (2, 10)");
                    statement.execute("INSERT INTO inventory (product_id, quantity) VALUES (1, 7), (2, 5)");
                    statement.execute("""
                            INSERT INTO orders (id, order_number, order_date, order_status, total_price, total_quantity, user_id)
                            VALUES (1, 'reserved', CURRENT_TIMESTAMP, 'PENDING_PAYMENT', 30, 3, 1),
                                   (2, 'released', CURRENT_TIMESTAMP, 'CANCELED', 20, 2, 1)
                            """);
                    statement.execute("""
                            INSERT INTO inventory_reservations (order_id, product_id, quantity, status, expires_at)
                            VALUES (1, 1, 3, 'RESERVED', CURRENT_TIMESTAMP + INTERVAL '15 minutes'),
                                   (2, 1, 2, 'RELEASED', CURRENT_TIMESTAMP)
                            """);
                }
                connection.commit();
                liquibase.update(new Contexts(), new LabelExpression());
                try (var statement = connection.createStatement();
                     var rows = statement.executeQuery("""
                             SELECT total_quantity, reserved_quantity FROM inventory ORDER BY product_id
                             """)) {
                    assertTrue(rows.next());
                    assertEquals(10, rows.getInt("total_quantity"));
                    assertEquals(3, rows.getInt("reserved_quantity"));
                    assertTrue(rows.next());
                    assertEquals(5, rows.getInt("total_quantity"));
                    assertEquals(0, rows.getInt("reserved_quantity"));
                    assertFalse(rows.next());
                }
                try (var statement = connection.createStatement()) {
                    assertThrows(java.sql.SQLException.class, () -> statement.execute(
                            "UPDATE inventory SET reserved_quantity = total_quantity + 1"));
                }
            }
        }
    }
}
