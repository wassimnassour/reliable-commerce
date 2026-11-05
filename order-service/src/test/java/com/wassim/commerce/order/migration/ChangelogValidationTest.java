package com.wassim.commerce.order.migration;

import liquibase.Contexts;
import liquibase.LabelExpression;
import liquibase.Liquibase;
import liquibase.database.DatabaseFactory;
import liquibase.database.OfflineConnection;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChangelogValidationTest {

    @AfterEach
    void cleanup() {
        File csv = new File("databasechangelog.csv");
        if (csv.exists()) {
            csv.delete();
        }
    }

    @Test
    void testChangelogIsValid() throws Exception {
        try (var resources = new ClassLoaderResourceAccessor()) {
            var offlineConn = new OfflineConnection("offline:postgresql", resources);
            var database = DatabaseFactory.getInstance().findCorrectDatabaseImplementation(offlineConn);
            try (var liquibase = new Liquibase("db/changelog/db.changelog-master.yaml", resources, database)) {
                var changeLog = liquibase.getDatabaseChangeLog();
                var changeSets = changeLog.getChangeSets();
                var outboxChangeSet = changeSets.stream()
                        .filter(cs -> "021-create-outbox-messages".equals(cs.getId()))
                        .findFirst()
                        .orElseThrow(() -> new AssertionError("021-create-outbox-messages changeSet not found"));

                assertEquals(3, outboxChangeSet.getChanges().size());
                assertTrue(outboxChangeSet.getChanges().get(0) instanceof liquibase.change.core.CreateTableChange);
                var createTable = (liquibase.change.core.CreateTableChange) outboxChangeSet.getChanges().get(0);
                assertEquals("outbox_messages", createTable.getTableName());

                var columns = createTable.getColumns();
                assertEquals(11, columns.size());

                // Verify id
                var idCol = columns.get(0);
                assertEquals("id", idCol.getName());
                assertEquals("BIGINT", idCol.getType());
                assertTrue(idCol.isAutoIncrement());
                assertTrue(idCol.getConstraints().isPrimaryKey());

                // Verify aggregate_type
                var aggTypeCol = columns.get(1);
                assertEquals("aggregate_type", aggTypeCol.getName());
                assertEquals("VARCHAR(50)", aggTypeCol.getType());
                assertFalse(aggTypeCol.getConstraints().isNullable());

                // Verify aggregate_id
                var aggIdCol = columns.get(2);
                assertEquals("aggregate_id", aggIdCol.getName());
                assertEquals("VARCHAR(100)", aggIdCol.getType());
                assertFalse(aggIdCol.getConstraints().isNullable());

                // Verify exchange
                var exchangeCol = columns.get(3);
                assertEquals("exchange", exchangeCol.getName());
                assertEquals("VARCHAR(100)", exchangeCol.getType());
                assertFalse(exchangeCol.getConstraints().isNullable());

                // Verify routing_key
                var routingKeyCol = columns.get(4);
                assertEquals("routing_key", routingKeyCol.getName());
                assertEquals("VARCHAR(100)", routingKeyCol.getType());
                assertFalse(routingKeyCol.getConstraints().isNullable());

                // Verify payload
                var payloadCol = columns.get(5);
                assertEquals("payload", payloadCol.getName());
                assertEquals("JSONB", payloadCol.getType());
                assertFalse(payloadCol.getConstraints().isNullable());

                // Verify status
                var statusCol = columns.get(6);
                assertEquals("status", statusCol.getName());
                assertEquals("VARCHAR(20)", statusCol.getType());
                assertFalse(statusCol.getConstraints().isNullable());
                assertEquals("PENDING", statusCol.getDefaultValue());

                // Verify retry_count
                var retryCountCol = columns.get(7);
                assertEquals("retry_count", retryCountCol.getName());
                assertEquals("INTEGER", retryCountCol.getType());
                assertFalse(retryCountCol.getConstraints().isNullable());
                assertEquals(0, retryCountCol.getDefaultValueNumeric().intValue());

                // Verify error_message
                var errorMsgCol = columns.get(8);
                assertEquals("error_message", errorMsgCol.getName());
                assertEquals("TEXT", errorMsgCol.getType());

                // Verify created_at
                var createdAtCol = columns.get(9);
                assertEquals("created_at", createdAtCol.getName());
                assertEquals("TIMESTAMP WITH TIME ZONE", createdAtCol.getType());
                assertFalse(createdAtCol.getConstraints().isNullable());

                // Verify processed_at
                var processedAtCol = columns.get(10);
                assertEquals("processed_at", processedAtCol.getName());
                assertEquals("TIMESTAMP WITH TIME ZONE", processedAtCol.getType());

                // Verify indexes
                assertTrue(outboxChangeSet.getChanges().get(1) instanceof liquibase.change.core.CreateIndexChange);
                var indexStatus = (liquibase.change.core.CreateIndexChange) outboxChangeSet.getChanges().get(1);
                assertEquals("idx_outbox_messages_status_created_at", indexStatus.getIndexName());

                assertTrue(outboxChangeSet.getChanges().get(2) instanceof liquibase.change.core.CreateIndexChange);
                var indexAggregate = (liquibase.change.core.CreateIndexChange) outboxChangeSet.getChanges().get(2);
                assertEquals("idx_outbox_messages_aggregate", indexAggregate.getIndexName());
            }
        }
    }
}
