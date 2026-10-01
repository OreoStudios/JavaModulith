package dev.oreo.javamodulith.core;
import java.util.*;
/** Optional publication tracking SPI. Incomplete entries are observable; automatic replay is not implemented. */
public interface EventJournal {
    UUID begin(String eventType, String listenerId, String payload);
    void complete(UUID id);
    void fail(UUID id, String message);
    long incompleteCount();
    static EventJournal noop() { return Noop.INSTANCE; }
    enum Noop implements EventJournal {
        INSTANCE;
        public UUID begin(String eventType, String listenerId, String payload) { return UUID.randomUUID(); }
        public void complete(UUID id) { }
        public void fail(UUID id, String message) { }
        public long incompleteCount() { return 0; }
    }
}
