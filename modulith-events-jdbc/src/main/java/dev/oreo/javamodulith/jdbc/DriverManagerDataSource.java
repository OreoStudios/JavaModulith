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
 * A production application may instead pass HikariCP or any other DataSource.
 *
 * Requires the matching database JDBC driver on the application's runtime classpath.
 */
public final class DriverManagerDataSource implements DataSource {
    private final String url;
    private final String username;
    private final String password;
    private volatile PrintWriter logWriter;
    private volatile int loginTimeout;

    public DriverManagerDataSource(String url) {
        this(url, null, null);
    }

    public DriverManagerDataSource(String url, String username, String password) {
        this.url = Objects.requireNonNull(url, "url");
        if (url.isBlank()) throw new IllegalArgumentException("JDBC URL cannot be blank");
        this.username = username;
        this.password = password;
    }

    @Override
    public Connection getConnection() throws SQLException {
        return username == null ? DriverManager.getConnection(url)
                : DriverManager.getConnection(url, username, password);
    }

    @Override
    public Connection getConnection(String user, String password) throws SQLException {
        return DriverManager.getConnection(url, user, password);
    }

    @Override public PrintWriter getLogWriter() { return logWriter; }
    @Override public void setLogWriter(PrintWriter writer) { logWriter = writer; }
    @Override public void setLoginTimeout(int timeout) {
        if (timeout < 0) throw new IllegalArgumentException("timeout must not be negative");
        loginTimeout = timeout;
    }
    @Override public int getLoginTimeout() { return loginTimeout; }
    @Override public Logger getParentLogger() throws SQLFeatureNotSupportedException {
        throw new SQLFeatureNotSupportedException("No parent logger");
    }
    @Override public <T> T unwrap(Class<T> type) throws SQLException {
        if (type.isInstance(this)) return type.cast(this);
        throw new SQLException("Not a wrapper for " + type.getName());
    }
    @Override public boolean isWrapperFor(Class<?> type) { return type.isInstance(this); }
}
