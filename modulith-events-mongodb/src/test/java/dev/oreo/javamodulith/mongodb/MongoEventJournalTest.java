package dev.oreo.javamodulith.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.result.UpdateResult;
import java.lang.reflect.Proxy;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MongoEventJournalTest {
    @SuppressWarnings("unchecked")
    @Test void tracksPublicationWithOfficialDriverInterfaces() {
        AtomicReference<Document> inserted = new AtomicReference<>();
        AtomicReference<Object> filter = new AtomicReference<>();
        MongoCollection<Document> collection = (MongoCollection<Document>) Proxy.newProxyInstance(
                MongoCollection.class.getClassLoader(), new Class<?>[]{MongoCollection.class},
                (proxy, method, args) -> switch (method.getName()) {
                    case "insertOne" -> { inserted.set((Document) args[0]); yield null; }
                    case "updateOne" -> {
                        filter.set(args[0]);
                        yield UpdateResult.acknowledged(1L, 1L, null);
                    }
                    case "countDocuments" -> 2L;
                    default -> throw new UnsupportedOperationException(method.getName());
                });
        MongoEventJournal journal = new MongoEventJournal(collection);
        UUID id = journal.begin("OrderPlaced", "audit", "payload");
        assertEquals(id.toString(), inserted.get().getString("_id"));
        assertEquals("PENDING", inserted.get().getString("status"));
        assertEquals(2L, journal.incompleteCount());
        journal.complete(id);
        assertNotNull(filter.get());
    }

    @SuppressWarnings("unchecked")
    @Test void rejectsAnUnknownOrAlreadyFinishedPublication() {
        MongoCollection<Document> collection = (MongoCollection<Document>) Proxy.newProxyInstance(
                MongoCollection.class.getClassLoader(), new Class<?>[]{MongoCollection.class},
                (proxy, method, args) -> {
                    if (method.getName().equals("updateOne")) return UpdateResult.acknowledged(0L, 0L, null);
                    throw new UnsupportedOperationException(method.getName());
                });
        MongoEventJournal journal = new MongoEventJournal(collection);
        assertThrows(IllegalStateException.class, () -> journal.complete(UUID.randomUUID()));
    }
}
