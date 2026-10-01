package dev.oreo.javamodulith.jdbc;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Real-database integration tests. In local builds these tests are skipped unless
 * the corresponding JDBC_URL, JDBC_USER and JDBC_PASSWORD are supplied.
 * The GitHub Actions JDBC integration job supplies all three databases.
 */
class JdbcIntegrationTest {
    @Test void postgresql() { exercise("POSTGRES", JdbcDialect.POSTGRESQL); }
    @Test void mariaDb() { exercise("MARIADB", JdbcDialect.MARIADB); }
    @Test void mysql() { exercise("MYSQL", JdbcDialect.MYSQL); }

    private static void exercise(String prefix, JdbcDialect dialect) {
        String url = System.getenv(prefix + "_JDBC_URL");
        assumeTrue(url != null && !url.isBlank(), prefix + "_JDBC_URL is not configured");
        String user = System.getenv(prefix + "_JDBC_USER");
        String password = System.getenv(prefix + "_JDBC_PASSWORD");
        String table = "jm_it_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        DriverManagerDataSource source = new DriverManagerDataSource(url, user, password);

        JdbcEventJournal journal = new JdbcEventJournal(source, dialect, table);
        UUID pending = journal.begin("OrderPlaced", "notification", "event payload");
        UUID completed = journal.begin("OrderPlaced", "billing", "event payload 2");
        UUID failed = journal.begin("OrderPlaced", "audit", "event payload 3");
        assertEquals(3, journal.incompleteCount());
        journal.complete(completed);
        journal.fail(failed, "simulated failure");
        assertEquals(2, journal.incompleteCount());
        assertEquals(2, journal.incomplete().size());

        // Verify that schema initialization and index creation are idempotent.
        JdbcEventJournal reopened = new JdbcEventJournal(source, dialect, table);
        assertEquals(2, reopened.incompleteCount());
        assertTrue(reopened.incomplete().stream().anyMatch(row ->
                row.id().equals(pending) && row.payload().equals("event payload")));
        assertTrue(reopened.incomplete().stream().anyMatch(row ->
                row.id().equals(failed) && "FAILED".equals(row.status())));
        reopened.complete(pending);
        assertEquals(1, reopened.incompleteCount());
        assertEquals(2, reopened.deleteCompletedBefore(Instant.now().plusSeconds(60)));
        assertEquals(1, reopened.incompleteCount());
    }
}
