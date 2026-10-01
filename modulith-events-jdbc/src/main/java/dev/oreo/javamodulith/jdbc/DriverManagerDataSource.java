package dev.oreo.javamodulith.jdbc;

import java.io.PrintWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.SQLFeatureNotSupportedException;
import java.util.Objects;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * Minimal DataSource for standalone apps and examples.
 * A production application may instead pass HikariCP or another pooled DataSource.
 *
 * Requires the matching JDBC driver on the application's runtime classpath.
 * DataSource logging and login timeout methods delegate to the JVM-global
 * DriverManager settings, not to a private connection pool.
 */
public final class DriverManagerDataSource implements DataSource {
    private final String url;
    private final String username;
    private final String password;

    public DriverManagerDataSource(String url) {
        this(url, null, null);
    }

    public DriverManagerDataSource(String url, String username, String password) {
        this.url = Objects.requireNonNull(url, "url");
        if (url.isBlank()) throw new IllegalArgumentException("JDBC URL cannot be blank");
        this.username = username;
        this.password = password;
    }

    @Override public Connection getConnection() throws SQLException {
        return username == null ? DriverManager.getConnection(url)
                : DriverManager.getConnection(url, username, password);
    }

    @Override public Connection getConnection(String user, String pass) throws SQLException {
        return DriverManager.getConnection(url, user, pass);
    }

    @Override public PrintWriter getLogWriter() { return DriverManager.getLogWriter(); }
    @Override public void setLogWriter(PrintWriter writer) { DriverManager.setLogWriter(writer); }
    @Override public void setLoginTimeout(int seconds) { DriverManager.setLoginTimeout(seconds); }
    @Override public int getLoginTimeout() { return DriverManager.getLoginTimeout(); }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("No parent logger");
    }
    @Override public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) return type.cast(this);
        throw new SQLException("Not a wrapper for " + type.getName());
    }
    @Override public boolean isWrapperFor(Class<?> type) { return type.isInstance(this); }
}
