package dev.freetrade.db;

import java.sql.Connection;
import java.sql.Driver;
import java.sql.SQLException;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import dev.freetrade.FreeTrade;

/**
 * One JDBC connection owned by a single worker thread. Every unit of work runs there inside its own
 * transaction, so the game thread never blocks on the database. Drivers are instantiated directly
 * (no DriverManager) because they live in the mod's Jar-in-Jar layer.
 */
public final class Database implements AutoCloseable {
    @FunctionalInterface
    public interface Work<T> {
        T run(Connection c) throws SQLException;
    }

    private final Dialect dialect;
    private final String url;
    private final Properties props;
    private final ExecutorService worker;
    private Connection connection;

    private Database(Dialect dialect, String url, Properties props) {
        this.dialect = dialect;
        this.url = url;
        this.props = props;
        this.worker = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "FreeTrade-DB");
            t.setDaemon(true);
            return t;
        });
    }

    public static Database sqlite(String file) {
        Properties p = new Properties();
        p.setProperty("busy_timeout", "10000");
        p.setProperty("journal_mode", "WAL");
        p.setProperty("foreign_keys", "true");
        return new Database(Dialect.SQLITE, "jdbc:sqlite:" + file, p);
    }

    public static Database postgres(String url, String user, String password) {
        Properties p = new Properties();
        p.setProperty("user", user);
        p.setProperty("password", password);
        p.setProperty("ApplicationName", "FreeTrade");
        p.setProperty("connectTimeout", "10");
        p.setProperty("tcpKeepAlive", "true");
        return new Database(Dialect.POSTGRES, url, p);
    }

    public Dialect dialect() {
        return dialect;
    }

    private Driver driver() throws SQLException {
        try {
            return switch (dialect) {
                case SQLITE -> (Driver) Class.forName("org.sqlite.JDBC").getDeclaredConstructor().newInstance();
                case POSTGRES -> (Driver) Class.forName("org.postgresql.Driver").getDeclaredConstructor().newInstance();
            };
        } catch (ReflectiveOperationException e) {
            throw new SQLException("JDBC driver not available for " + dialect, e);
        }
    }

    private Connection connection() throws SQLException {
        if (connection != null) {
            boolean valid;
            try {
                valid = !connection.isClosed() && connection.isValid(5);
            } catch (SQLException e) {
                valid = false;
            }
            if (valid) return connection;
            try {
                connection.close();
            } catch (SQLException ignored) {
            }
            connection = null;
            FreeTrade.LOG.warn("Database connection lost, reconnecting");
        }
        Connection c = driver().connect(url, props);
        if (c == null) throw new SQLException("Driver refused url " + url);
        c.setAutoCommit(false);
        if (dialect == Dialect.POSTGRES) c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
        connection = c;
        return c;
    }

    private <T> T runInTx(Work<T> work) throws SQLException {
        SQLException last = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            Connection c = connection();
            try {
                T result = work.run(c);
                c.commit();
                return result;
            } catch (SQLException e) {
                last = e;
                try {
                    c.rollback();
                } catch (SQLException ignored) {
                }
                // 40001 serialization failure / 40P01 deadlock / 08xxx connection: retry
                String state = e.getSQLState();
                boolean retry = state != null && (state.startsWith("40") || state.startsWith("08"));
                if (!retry) throw e;
                if (state.startsWith("08")) connection = null;
            }
        }
        throw last;
    }

    /** Runs the work in a transaction on the database thread. */
    public <T> CompletableFuture<T> submit(Work<T> work) {
        CompletableFuture<T> f = new CompletableFuture<>();
        try {
            worker.execute(() -> {
                try {
                    f.complete(runInTx(work));
                } catch (Throwable t) {
                    f.completeExceptionally(t);
                }
            });
        } catch (java.util.concurrent.RejectedExecutionException e) {
            f.completeExceptionally(e);
        }
        return f;
    }

    /** Blocking variant for startup/shutdown and tests. */
    public <T> T call(Work<T> work) throws SQLException {
        try {
            return submit(work).get(60, TimeUnit.SECONDS);
        } catch (java.util.concurrent.ExecutionException e) {
            if (e.getCause() instanceof SQLException s) throw s;
            throw new SQLException(e.getCause());
        } catch (Exception e) {
            throw new SQLException(e);
        }
    }

    @Override
    public void close() {
        worker.execute(() -> {
            if (connection != null) {
                try {
                    connection.close();
                } catch (SQLException ignored) {
                }
                connection = null;
            }
        });
        worker.shutdown();
        try {
            worker.awaitTermination(30, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
