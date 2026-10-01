package dev.oreo.javamodulith.mongodb;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Indexes;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import dev.oreo.javamodulith.core.EventJournal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.bson.Document;

/**
 * MongoDB publication tracking through the synchronous driver.
 *
 * Acknowledged write concern is recommended. This journal does not serialize
 * events beyond the supplied string payload, and does not automatically replay
 * unfinished publications. Do not perform synchronous network I/O on game render threads.
 * The MongoClient/MongoDatabase lifecycle belongs to the host application.
 */
public final class MongoEventJournal implements EventJournal {
    public static final String DEFAULT_COLLECTION = "modulith_event_publications";
    private final MongoCollection<Document> collection;

    public MongoEventJournal(MongoDatabase database) {
        this(Objects.requireNonNull(database, "database").getCollection(DEFAULT_COLLECTION));
    }

    public MongoEventJournal(MongoDatabase database, String collectionName) {
        this(Objects.requireNonNull(database, "database").getCollection(
                Objects.requireNonNull(collectionName, "collectionName")));
    }

    public MongoEventJournal(MongoCollection<Document> collection) {
        this.collection = Objects.requireNonNull(collection, "collection");
    }

    /** Optional index for frequently queried incomplete publications. Requires DB access. */
    public void ensureIndexes() {
        collection.createIndex(Indexes.compoundIndex(
                Indexes.ascending("status"), Indexes.ascending("createdAt")));
    }

    @Override
    public UUID begin(String eventType, String listenerId, String payload) {
        Objects.requireNonNull(eventType, "eventType");
        Objects.requireNonNull(listenerId, "listenerId");
        UUID id = UUID.randomUUID();
        collection.insertOne(new Document("_id", id.toString())
                .append("eventType", eventType)
                .append("listenerId", listenerId)
                .append("payload", payload)
                .append("status", "PENDING")
                .append("createdAt", new Date()));
        return id;
    }

    @Override public void complete(UUID id) { update(id, "COMPLETED", null); }
    @Override public void fail(UUID id, String message) { update(id, "FAILED", message); }

    private void update(UUID id, String status, String error) {
        Objects.requireNonNull(id, "id");
        long modified = collection.updateOne(
                Filters.and(Filters.eq("_id", id.toString()), Filters.eq("status", "PENDING")),
                Updates.combine(Updates.set("status", status),
                        Updates.set("finishedAt", new Date()), Updates.set("error", error)))
                .getModifiedCount();
        if (modified != 1) {
            throw new IllegalStateException("Unknown or already-finished publication: " + id);
        }
    }

    @Override
    public long incompleteCount() {
        return collection.countDocuments(Filters.in("status", "PENDING", "FAILED"));
    }

    /** Incomplete or failed journal entries; for diagnostics/manual recovery only. */
    public List<Publication> incomplete() {
        List<Publication> entries = new ArrayList<>();
        for (Document doc : collection.find(Filters.in("status", "PENDING", "FAILED"))
                .sort(Sorts.ascending("createdAt"))) {
            entries.add(new Publication(
                    UUID.fromString(doc.getString("_id")),
                    doc.getString("eventType"), doc.getString("listenerId"),
                    doc.getString("payload"), doc.getString("status"),
                    doc.getDate("createdAt"), doc.getDate("finishedAt"), doc.getString("error")));
        }
        return List.copyOf(entries);
    }

    public record Publication(UUID id, String eventType, String listenerId,
                              String payload, String status, Date createdAt,
                              Date finishedAt, String error) { }
}
