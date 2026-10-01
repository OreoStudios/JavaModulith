package dev.oreo.javamodulith.jdbc;

import dev.oreo.javamodulith.core.EventJournal;
import dev.oreo.javamodulith.core.ModuleRuntime;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

class JdbcEventJournalTest {
    @TempDir Path directory;

    private JdbcEventJournal sqlite() {
        return new JdbcEventJournal(
                new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("events.db")),
                JdbcDialect.SQLITE);
    }

    @Test void handlesPendingCompletedFailedAndRestarts() {
        JdbcEventJournal journal = sqlite();
        UUID pending = journal.begin("Purchase", "email", "P-1");
        UUID completed = journal.begin("Purchase", "analytics", "P-2");
        UUID failed = journal.begin("Purchase", "audit", "P-3");
        assertEquals(3, journal.incompleteCount());
        journal.complete(completed);
        journal.fail(failed, "listener crashed");

        assertEquals(2, journal.incompleteCount());
        var entries = journal.incomplete();
        assertEquals(2, entries.size());
        assertTrue(entries.stream().anyMatch(e -> e.id().equals(pending)
                && e.status().equals("PENDING") && e.finishedAt() == null));
        assertTrue(entries.stream().anyMatch(e -> e.id().equals(failed)
                && e.status().equals("FAILED") && e.error().equals("listener crashed")
                && e.finishedAt() != null));

        // The constructor can run schema/index creation repeatedly without erasing previous rows.
        JdbcEventJournal reopened = sqlite();
        assertEquals(2, reopened.incompleteCount());
        assertThrows(IllegalStateException.class, () -> reopened.complete(completed));
        assertThrows(IllegalStateException.class, () -> reopened.fail(failed, "again"));
        reopened.complete(pending);
        assertEquals(1, reopened.incompleteCount());

        assertEquals(2, reopened.deleteCompletedBefore(Instant.now().plusSeconds(60)));
        assertEquals(0, reopened.deleteCompletedBefore(Instant.now().plusSeconds(60)));
        assertEquals(1, reopened.incompleteCount()); // failed publication was not purged
    }

    @Test void canBeUsedAsTheRuntimeEventJournal() {
        EventJournal journal = sqlite();
        try (ModuleRuntime runtime = ModuleRuntime.builder().eventJournal(journal).start()) {
            var received = new java.util.concurrent.atomic.AtomicInteger();
            runtime.events().subscribe(String.class, message -> received.incrementAndGet());
            assertEquals(1, runtime.events().publish("hello").completedCount());
            assertEquals(1, received.get());
            assertEquals(0, runtime.diagnostics().incompletePublications());
        }
    }

    @Test void validatesTableNamesBeforeExecutingSql() {
        var source = new DriverManagerDataSource("jdbc:sqlite:" + directory.resolve("safe.db"));
        assertThrows(IllegalArgumentException.class,
                () -> new JdbcEventJournal(source, JdbcDialect.SQLITE, "bad;DROP TABLE x"));
        assertThrows(IllegalArgumentException.class,
                () -> new JdbcEventJournal(source, JdbcDialect.SQLITE, "tenant.events"));
        assertThrows(IllegalArgumentException.class,
                () -> new JdbcEventJournal(source, JdbcDialect.SQLITE, ""));
        var custom = new JdbcEventJournal(source, JdbcDialect.SQLITE, "custom_events");
        assertEquals("custom_events", custom.tableName());
        assertEquals(0, custom.incompleteCount());
    }
}
