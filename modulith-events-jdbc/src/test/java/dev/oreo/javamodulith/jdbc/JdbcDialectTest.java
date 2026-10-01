package dev.oreo.javamodulith.jdbc;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class JdbcDialectTest {
    @Test void eachDialectDefinesACompatibleTableAndStateIndex() {
        for (JdbcDialect dialect : JdbcDialect.values()) {
            String sql = dialect.createTableSql("my_publications");
            assertTrue(sql.contains("CREATE TABLE IF NOT EXISTS my_publications"));
            assertTrue(sql.contains("created_at BIGINT"));
            assertTrue(sql.contains("finished_at BIGINT"));
            assertTrue(sql.contains("id VARCHAR(36) PRIMARY KEY"));
            assertTrue(dialect.createIndexSql("my_publications").contains("ON my_publications (status, created_at)"));
        }
    }

    @Test void mysqlAndMariaDbUseLongTextForLargerEventPayloads() {
        assertTrue(JdbcDialect.MYSQL.createTableSql("events").contains("payload LONGTEXT"));
        assertTrue(JdbcDialect.MARIADB.createTableSql("events").contains("payload LONGTEXT"));
        assertTrue(JdbcDialect.POSTGRESQL.createTableSql("events").contains("payload TEXT"));
        assertTrue(JdbcDialect.SQLITE.createTableSql("events").contains("payload TEXT"));
        assertFalse(JdbcDialect.MYSQL.supportsConditionalIndexCreation());
        assertFalse(JdbcDialect.MARIADB.supportsConditionalIndexCreation());
        assertTrue(JdbcDialect.POSTGRESQL.supportsConditionalIndexCreation());
        assertTrue(JdbcDialect.SQLITE.supportsConditionalIndexCreation());
    }
}
