package dev.oreo.javamodulith.sqlite;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import static org.junit.jupiter.api.Assertions.*;
class SqliteEventJournalTest {
    @Test void savesAndCompletesPublications() throws Exception {
        var path=Files.createTempFile("javamodulith-", ".db");
        try {
            var journal=new SqliteEventJournal(path);
            var ok=journal.begin("Event","listener-a","payload");
            var failed=journal.begin("Event","listener-b","payload");
            assertEquals(2,journal.incompleteCount());
            journal.complete(ok); journal.fail(failed,"failed");
            assertEquals(1,journal.incompleteCount());
            assertEquals("FAILED",journal.incomplete().getFirst().status());
        } finally { Files.deleteIfExists(path); }
    }
}
