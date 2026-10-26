package com.wassim.commerce.order.migration;

import liquibase.ChecksumVersion;
import liquibase.change.AddColumnConfig;
import liquibase.change.ConstraintsConfig;
import liquibase.change.core.AddColumnChange;
import liquibase.changelog.ChangeLogParameters;
import liquibase.changelog.ChangeSet;
import liquibase.parser.ChangeLogParserFactory;
import liquibase.resource.ClassLoaderResourceAccessor;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.junit.jupiter.api.Assertions.*;

class PasswordMigrationTest {
    @Test
    void preservesOriginalChangesetChecksumAndParsesLegacyPreparation() throws Exception {
        try (var resources = new ClassLoaderResourceAccessor()) {
            var path = "db/changelog/010-add-missing-password-coumn.yaml";
            var log = ChangeLogParserFactory.getInstance().getParser(path, resources)
                    .parse(path, new ChangeLogParameters(), resources);
            assertEquals(2, log.getChangeSets().size());
            var original = new ChangeSet("010-add-password-column-to-users", "wassimnassour",
                    false, false, path, null, null, log);
            var change = new AddColumnChange();
            change.setTableName("users");
            var column = new AddColumnConfig();
            column.setName("password");
            column.setType("varchar(255)");
            column.setConstraints(new ConstraintsConfig().setNullable(false));
            change.addColumn(column);
            original.addChange(change);
            assertEquals(original.generateCheckSum(ChecksumVersion.latest()),
                    log.getChangeSets().get(1).generateCheckSum(ChecksumVersion.latest()));
            assertNotNull(log.getChangeSets().get(0).getPreconditions());
            assertNotNull(log.getChangeSets().get(1).getPreconditions());
        }
    }

    @Test
    void legacyMarkerCannotAuthenticateAsAPassword() {
        var encoder = new BCryptPasswordEncoder();
        assertFalse(encoder.matches("!", "!"));
        assertFalse(encoder.matches("password", "!"));
    }
}
