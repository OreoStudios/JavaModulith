package dev.oreo.javamodulith.jdbc;

import java.util.Objects;

/**
 * SQL dialect choices for the shared event publication table.
 *
 * All timestamps are stored as epoch milliseconds (BIGINT) to keep the
 * publication representation portable across databases and time zones.
 */
public enum JdbcDialect {
    POSTGRESQL("TEXT", true),
    MARIADB("LONGTEXT", false),
    MYSQL("LONGTEXT", false),
    SQLITE("TEXT", true);

    private final String textType;
    private final boolean supportsConditionalIndexCreation;

    JdbcDialect(String textType, boolean supportsConditionalIndexCreation) {
        this.textType = textType;
        this.supportsConditionalIndexCreation = supportsConditionalIndexCreation;
    }

    public boolean supportsConditionalIndexCreation() {
        return supportsConditionalIndexCreation;
    }

    /** SQL identifiers are validated by JdbcEventJournal before being interpolated. */
    public String createTableSql(String table) {
        Objects.requireNonNull(table, "table");
        return """
                CREATE TABLE IF NOT EXISTS %s (
                    id VARCHAR(36) PRIMARY KEY,
                    event_type VARCHAR(512) NOT NULL,
                    listener_id VARCHAR(512) NOT NULL,
                    payload %s,
                    status VARCHAR(16) NOT NULL,
                    created_at BIGINT NOT NULL,
                    finished_at BIGINT,
                    error_message %s
                )
                """.formatted(table, textType, textType);
    }

    public String createIndexSql(String table) {
        Objects.requireNonNull(table, "table");
        return "CREATE INDEX " + (supportsConditionalIndexCreation ? "IF NOT EXISTS " : "")
                + "idx_" + table + "_state ON " + table + " (status, created_at)";
    }
}
