package dev.oreo.javamodulith.jdbc;

import dev.oreo.javamodulith.core.EventJournal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import javax.sql.DataSource;

/**
 * One portable JDBC implementation of JavaModulith's EventJournal.
 *
 * <p>Supports PostgreSQL, MariaDB, MySQL and SQLite through an application-owned
 * DataSource, with dialect-specific DDL and a shared prepared-statement DML path.</p>
 *
 * <p>This is per-listener publication tracking, NOT a transaction-bound outbox.
 * Connections returned by the DataSource must have auto-commit enabled; no event
 * payload deserialization or automatic replay is performed. Database access is
 * synchronous and must not run on LibGDX/jME rendering threads.</p>
 *
 * <p>Creating a journal creates the table and status index if missing. For restricted
 * production accounts, provision the DDL separately or use an account permitted
 * to create that table and index before switching to a restricted account.</p>
 */
public final class JdbcEventJournal implements EventJournal {
    public static final String DEFAULT_TABLE = "modulith_event_publications";

    private final DataSource dataSource;
    private final JdbcDialect dialect;
    private final String tableName;

    public JdbcEventJournal(DataSource dataSource, JdbcDialect dialect) {
        this(dataSource, dialect, DEFAULT_TABLE);
    }

    /**
     * @param tableName unqualified SQL identifier. Only letters, digits and '_'
     *                  are accepted; length is capped to fit portable index names.
     */
    public JdbcEventJournal(DataSource dataSource, JdbcDialect dialect, String tableName) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.dialect = Objects.requireNonNull(dialect, "dialect");
        this.tableName = validateTableName(tableName);
        initializeSchema();
    }

    private static String validateTableName(String value) {
        Objects.requireNonNull(value, "tableName");
        if (!value.matches("[A-Za-z_][A-Za-z0-9_]{0,44}")) {
            throw new IllegalArgumentException("Invalid JDBC table name (unqualified identifier, max 45 chars): " + value);
        }
        return value;
    }

    public JdbcDialect dialect() { return dialect; }
    public String tableName() { return tableName; }

    /** Schema creation is safe to repeat for the same dialect and table. */
    public void initializeSchema() {
        try (Connection connection = open(); Statement statement = connection.createStatement()) {
            statement.execute(dialect.createTableSql(tableName));
            try {
                statement.execute(dialect.createIndexSql(tableName));
            } catch (SQLException exception) {
                // MySQL and MariaDB do not support CREATE INDEX IF NOT EXISTS.
                // Error 1061 means an earlier initialization already created the index.
                boolean duplicateIndex = (dialect == JdbcDialect.MYSQL || dialect == JdbcDialect.MARIADB)
                        && exception.getErrorCode() == 1061;
                if (!duplicateIndex) throw exception;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot initialize JDBC event journal (" + dialect + ")", exception);
        }
    }

    /**
     * The journal deliberately owns one independent connection per operation and
     * does not commit, reuse or mutate an application transaction connection.
     */
    private Connection open() throws SQLException {
        Connection connection = dataSource.getConnection();
        try {
            if (!connection.getAutoCommit()) {
                throw new SQLException("JdbcEventJournal requires auto-commit DataSource connections; "
                        + "it does not join application transactions");
            }
            return connection;
        } catch (SQLException exception) {
            try { connection.close(); }
            catch (SQLException closeFailure) { exception.addSuppressed(closeFailure); }
            throw exception;
        }
    }

    @Override
    public UUID begin(String eventType, String listenerId, String payload) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listenerId, "listenerId");
        UUID id = UUID.randomUUID();
        String sql = "INSERT INTO " + tableName
                + " (id, event_type, listener_id, payload, status, created_at)"
                + " VALUES (?, ?, ?, ?, ?, ?)";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, id.toString());
            statement.setString(2, eventType);
            statement.setString(3, listenerId);
            statement.setString(4, payload);
            statement.setString(5, "PENDING");
            statement.setLong(6, Instant.now().toEpochMilli());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Event publication was not inserted: " + id);
            }
            return id;
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot record JDBC event publication", exception);
        }
    }

    @Override public void complete(UUID id) { update(id, "COMPLETED", null); }
    @Override public void fail(UUID id, String message) { update(id, "FAILED", message); }

    private void update(UUID id, String status, String message) {
        Objects.requireNonNull(id, "id");
        String sql = "UPDATE " + tableName
                + " SET status = ?, finished_at = ?, error_message = ?"
                + " WHERE id = ? AND status = 'PENDING'";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status);
            statement.setLong(2, Instant.now().toEpochMilli());
            statement.setString(3, message);
            statement.setString(4, id.toString());
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("Unknown or already-finalized publication: " + id);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot finalize JDBC event publication " + id, exception);
        }
    }

    @Override
    public long incompleteCount() {
        String sql = "SELECT COUNT(*) FROM " + tableName + " WHERE status IN ('PENDING', 'FAILED')";
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            if (!rows.next()) throw new IllegalStateException("Missing JDBC count result");
            return rows.getLong(1);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot count incomplete JDBC event publications", exception);
        }
    }

    /** Returns a stable snapshot of pending/failed publications for diagnostics or manual recovery. */
    public List<Publication> incomplete() {
        String sql = "SELECT id, event_type, listener_id, payload, status, created_at,"
                + " finished_at, error_message FROM " + tableName
                + " WHERE status IN ('PENDING', 'FAILED') ORDER BY created_at ASC, id ASC";
        List<Publication> result = new ArrayList<>();
        try (Connection connection = open();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                long finishedMillis = rows.getLong(7);
                Instant finishedAt = rows.wasNull() ? null : Instant.ofEpochMilli(finishedMillis);
                result.add(new Publication(
                        UUID.fromString(rows.getString(1)),
                        rows.getString(2),
                        rows.getString(3),
                        rows.getString(4),
                        rows.getString(5),
                        Instant.ofEpochMilli(rows.getLong(6)),
                        finishedAt,
                        rows.getString(8)));
            }
            return List.copyOf(result);
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot read incomplete JDBC event publications", exception);
        }
    }

    /** Explicit housekeeping. Never removes PENDING or FAILED entries. */
    public int deleteCompletedBefore(Instant cutoff) {
        Objects.requireNonNull(cutoff, "cutoff");
        String sql = "DELETE FROM " + tableName
                + " WHERE status = 'COMPLETED' AND finished_at < ?";
        try (Connection connection = open(); PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setLong(1, cutoff.toEpochMilli());
            return statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException("Cannot purge completed JDBC event publications", exception);
        }
    }

    public record Publication(
            UUID id, String eventType, String listenerId, String payload,
            String status, Instant createdAt, Instant finishedAt, String error
    ) { }
}
