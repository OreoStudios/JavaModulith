package dev.oreo.javamodulith.sqlite;
import dev.oreo.javamodulith.core.EventJournal;
import java.nio.file.*;
import java.sql.*;
import java.time.Instant;
import java.util.*;
/** SQLite-backed publication journal. Requires sqlite-jdbc at runtime, does not automatically replay events. */
public final class SqliteEventJournal implements EventJournal {
    private final String jdbcUrl;
    public SqliteEventJournal(Path path) {
        Objects.requireNonNull(path);
        try {
            Path absolute=path.toAbsolutePath();
            if (absolute.getParent()!=null) Files.createDirectories(absolute.getParent());
            this.jdbcUrl="jdbc:sqlite:"+absolute;
            try (Connection conn=open(); Statement statement=conn.createStatement()) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS event_publications (
                      id TEXT PRIMARY KEY, event_type TEXT NOT NULL, listener_id TEXT NOT NULL,
                      payload TEXT, status TEXT NOT NULL, created_at TEXT NOT NULL,
                      finished_at TEXT, error TEXT)
                    """);
            }
        } catch (SQLException | java.io.IOException exception) { throw new IllegalStateException("Cannot initialize SQLite journal",exception); }
    }
    private Connection open() throws SQLException { return DriverManager.getConnection(jdbcUrl); }
    @Override public UUID begin(String eventType,String listenerId,String payload) {
        UUID id=UUID.randomUUID();
        try (Connection conn=open(); PreparedStatement statement=conn.prepareStatement(
            "INSERT INTO event_publications(id,event_type,listener_id,payload,status,created_at) VALUES(?,?,?,?,?,?)")) {
            statement.setString(1,id.toString()); statement.setString(2,eventType); statement.setString(3,listenerId);
            statement.setString(4,payload); statement.setString(5,"PENDING"); statement.setString(6,Instant.now().toString());
            statement.executeUpdate(); return id;
        } catch (SQLException exception) { throw new IllegalStateException("Cannot create event publication",exception); }
    }
    @Override public void complete(UUID id) { update(id,"COMPLETED",null); }
    @Override public void fail(UUID id,String error) { update(id,"FAILED",error); }
    private void update(UUID id,String status,String error) {
        try (Connection conn=open(); PreparedStatement statement=conn.prepareStatement(
            "UPDATE event_publications SET status=?, finished_at=?, error=? WHERE id=?")) {
            statement.setString(1,status); statement.setString(2,Instant.now().toString());
            statement.setString(3,error); statement.setString(4,id.toString());
            if (statement.executeUpdate()!=1) throw new IllegalStateException("Unknown publication id: "+id);
        } catch (SQLException exception) { throw new IllegalStateException("Cannot update event publication",exception); }
    }
    @Override public long incompleteCount() {
        try (Connection conn=open(); PreparedStatement statement=conn.prepareStatement(
            "SELECT COUNT(*) FROM event_publications WHERE status IN ('PENDING','FAILED')");
            ResultSet results=statement.executeQuery()) {
            return results.next()?results.getLong(1):0;
        } catch (SQLException exception) { throw new IllegalStateException("Cannot count event publications",exception); }
    }
    /** Query incomplete publication records for manual recovery/diagnostics. */
    public List<Publication> incomplete() {
        List<Publication> output=new ArrayList<>();
        try (Connection conn=open(); PreparedStatement statement=conn.prepareStatement(
            "SELECT id,event_type,listener_id,payload,status,created_at,finished_at,error FROM event_publications WHERE status IN ('PENDING','FAILED') ORDER BY created_at");
            ResultSet rows=statement.executeQuery()) {
            while(rows.next()) output.add(new Publication(UUID.fromString(rows.getString(1)),rows.getString(2),
                rows.getString(3),rows.getString(4),rows.getString(5),rows.getString(6),rows.getString(7),rows.getString(8)));
        } catch (SQLException exception) { throw new IllegalStateException("Cannot read publications",exception); }
        return List.copyOf(output);
    }
    public record Publication(UUID id,String eventType,String listenerId,String payload,String status,
                              String createdAt,String finishedAt,String error) { }
}
