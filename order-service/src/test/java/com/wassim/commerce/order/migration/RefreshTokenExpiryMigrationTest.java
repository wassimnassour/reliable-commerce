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
import java.time.Instant;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class RefreshTokenExpiryMigrationTest {
    @Container
    static final PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:17");

    @Test
    void preservesExistingUtcExpiryRegardlessOfDatabaseSessionTimezone() throws Exception {
        try (var connection = DriverManager.getConnection(postgres.getJdbcUrl(),
                postgres.getUsername(), postgres.getPassword());
             var resources = new ClassLoaderResourceAccessor()) {
            try (var statement = connection.createStatement()) {
                statement.execute("SET TIME ZONE 'America/New_York'");
                statement.execute("CREATE TABLE refresh_token (expires_at timestamp NOT NULL)");
                statement.execute("INSERT INTO refresh_token VALUES (TIMESTAMP '2026-10-27 12:34:56')");
            }
            var database = DatabaseFactory.getInstance()
                    .findCorrectDatabaseImplementation(new JdbcConnection(connection));
            try (var liquibase = new Liquibase("db/changelog/016-refresh-token-expiry-timezone.yaml",
                    resources, database)) {
                liquibase.update(new Contexts(), new LabelExpression());
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("SELECT expires_at FROM refresh_token")) {
                    assertTrue(result.next());
                    assertEquals(Instant.parse("2026-10-27T12:34:56Z"),
                            result.getObject(1, OffsetDateTime.class).toInstant());
                }
                try (var statement = connection.createStatement();
                     var result = statement.executeQuery("""
                             SELECT data_type FROM information_schema.columns
                             WHERE table_schema = 'public' AND table_name = 'refresh_token'
                               AND column_name = 'expires_at'
                             """)) {
                    assertTrue(result.next());
                    assertEquals("timestamp with time zone", result.getString(1));
                }
            }
        }
    }
}
