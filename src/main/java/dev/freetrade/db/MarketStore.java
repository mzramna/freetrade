package dev.freetrade.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.freetrade.market.BackoffTracker;

/**
 * SQL for the shared trade balance. Every method takes the connection of the current transaction
 * (see {@link Database#submit}). Row locks ({@code FOR UPDATE} on Postgres) keep several servers sharing
 * one database consistent.
 */
public final class MarketStore {
    public static final int SCHEMA_VERSION = 1;
    public static final String TREASURY = "treasury";
    public static final String TAX = "tax";

    private final Dialect d;

    public MarketStore(Dialect dialect) {
        this.d = dialect;
    }

    public void migrate(Connection c, long treasuryInitialMilli) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.execute("CREATE TABLE IF NOT EXISTS ft_meta (k VARCHAR(64) PRIMARY KEY, v TEXT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS ft_stock (item VARCHAR(255) PRIMARY KEY, stock BIGINT NOT NULL, "
                    + "updated_ms BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS ft_base_values (item VARCHAR(255) PRIMARY KEY, base_value DOUBLE PRECISION "
                    + "NOT NULL, source VARCHAR(16) NOT NULL, derivation TEXT)");
            s.execute("CREATE TABLE IF NOT EXISTS ft_accounts (name VARCHAR(64) PRIMARY KEY, balance BIGINT NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS ft_transactions (id " + d.autoIncrementPk + ", ts BIGINT NOT NULL, "
                    + "server_id VARCHAR(64) NOT NULL, player VARCHAR(64), player_name VARCHAR(64), source VARCHAR(32) NOT NULL, "
                    + "item VARCHAR(255) NOT NULL, qty BIGINT NOT NULL, amount BIGINT NOT NULL, tax BIGINT NOT NULL, "
                    + "stock_after BIGINT NOT NULL)");
            s.execute("CREATE INDEX IF NOT EXISTS ft_transactions_ts ON ft_transactions (ts)");
            s.execute("CREATE INDEX IF NOT EXISTS ft_transactions_item ON ft_transactions (item, ts)");
            s.execute("CREATE TABLE IF NOT EXISTS ft_backoff (player VARCHAR(64) NOT NULL, item VARCHAR(255) NOT NULL, "
                    + "last_sell BIGINT NOT NULL, last_rt BIGINT NOT NULL, round_trips INT NOT NULL, lvl INT NOT NULL, "
                    + "PRIMARY KEY (player, item))");
            s.execute("CREATE TABLE IF NOT EXISTS ft_price_overrides (item VARCHAR(255) PRIMARY KEY, "
                    + "price DOUBLE PRECISION NOT NULL)");
            s.execute("CREATE TABLE IF NOT EXISTS ft_triggers (id VARCHAR(64) PRIMARY KEY, account VARCHAR(64) NOT NULL, "
                    + "op VARCHAR(2) NOT NULL, threshold BIGINT NOT NULL, cost BIGINT NOT NULL, mode VARCHAR(8) NOT NULL, "
                    + "cooldown_ms BIGINT NOT NULL, command TEXT NOT NULL, fired INT NOT NULL, last_fired BIGINT NOT NULL, "
                    + "armed INT NOT NULL, enabled INT NOT NULL)");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ft_meta (k, v) VALUES ('schema', ?) ON CONFLICT (k) DO NOTHING")) {
            ps.setString(1, Integer.toString(SCHEMA_VERSION));
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ft_accounts (name, balance) VALUES (?, ?) ON CONFLICT (name) DO NOTHING")) {
            ps.setString(1, TREASURY);
            ps.setLong(2, Math.max(0, treasuryInitialMilli));
            ps.executeUpdate();
            ps.setString(1, TAX);
            ps.setLong(2, 0);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- stock

    public Map<String, Long> loadStock(Connection c) throws SQLException {
        Map<String, Long> out = new HashMap<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT item, stock FROM ft_stock")) {
            while (rs.next()) out.put(rs.getString(1), rs.getLong(2));
        }
        return out;
    }

    /** Inserts stock rows that do not exist yet; existing rows are untouched. */
    public void ensureStock(Connection c, Map<String, Long> initial) throws SQLException {
        if (initial.isEmpty()) return;
        long now = System.currentTimeMillis();
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ft_stock (item, stock, updated_ms) VALUES (?, ?, ?) ON CONFLICT (item) DO NOTHING")) {
            int n = 0;
            for (var e : initial.entrySet()) {
                ps.setString(1, e.getKey());
                ps.setLong(2, e.getValue());
                ps.setLong(3, now);
                ps.addBatch();
                if (++n % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    /** Current stock with a row lock; creates the row with {@code initial} when missing. */
    public long lockStock(Connection c, String item, long initial) throws SQLException {
        ensureStock(c, Map.of(item, initial));
        try (PreparedStatement ps = c.prepareStatement("SELECT stock FROM ft_stock WHERE item = ?" + d.forUpdate)) {
            ps.setString(1, item);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public long addStock(Connection c, String item, long delta) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE ft_stock SET stock = stock + ?, updated_ms = ? WHERE item = ? RETURNING stock")) {
            ps.setLong(1, delta);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, item);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public void setStock(Connection c, String item, long stock) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ft_stock (item, stock, updated_ms) VALUES (?, ?, ?) "
                        + "ON CONFLICT (item) DO UPDATE SET stock = excluded.stock, updated_ms = excluded.updated_ms")) {
            ps.setString(1, item);
            ps.setLong(2, stock);
            ps.setLong(3, System.currentTimeMillis());
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- base values

    public record StoredValue(double value, String source, String derivation) {}

    public Map<String, StoredValue> loadBaseValues(Connection c) throws SQLException {
        Map<String, StoredValue> out = new HashMap<>();
        try (Statement s = c.createStatement();
             ResultSet rs = s.executeQuery("SELECT item, base_value, source, derivation FROM ft_base_values")) {
            while (rs.next()) out.put(rs.getString(1), new StoredValue(rs.getDouble(2), rs.getString(3), rs.getString(4)));
        }
        return out;
    }

    public void replaceBaseValues(Connection c, Map<String, StoredValue> values) throws SQLException {
        try (Statement s = c.createStatement()) {
            s.executeUpdate("DELETE FROM ft_base_values");
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ft_base_values (item, base_value, source, derivation) VALUES (?, ?, ?, ?)")) {
            int n = 0;
            for (var e : values.entrySet()) {
                ps.setString(1, e.getKey());
                ps.setDouble(2, e.getValue().value());
                ps.setString(3, e.getValue().source());
                ps.setString(4, e.getValue().derivation());
                ps.addBatch();
                if (++n % 500 == 0) ps.executeBatch();
            }
            ps.executeBatch();
        }
    }

    // ---------------------------------------------------------------- forced prices

    public Map<String, Double> loadOverrides(Connection c) throws SQLException {
        Map<String, Double> out = new HashMap<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT item, price FROM ft_price_overrides")) {
            while (rs.next()) out.put(rs.getString(1), rs.getDouble(2));
        }
        return out;
    }

    public void setOverride(Connection c, String item, double price) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ft_price_overrides (item, price) VALUES (?, ?) "
                + "ON CONFLICT (item) DO UPDATE SET price = excluded.price")) {
            ps.setString(1, item);
            ps.setDouble(2, price);
            ps.executeUpdate();
        }
    }

    public boolean deleteOverride(Connection c, String item) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM ft_price_overrides WHERE item = ?")) {
            ps.setString(1, item);
            return ps.executeUpdate() > 0;
        }
    }

    // ---------------------------------------------------------------- accounts

    public long lockAccount(Connection c, String name) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT balance FROM ft_accounts WHERE name = ?" + d.forUpdate)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return rs.getLong(1);
            }
        }
        try (PreparedStatement ps = c.prepareStatement(
                "INSERT INTO ft_accounts (name, balance) VALUES (?, 0) ON CONFLICT (name) DO NOTHING")) {
            ps.setString(1, name);
            ps.executeUpdate();
        }
        return 0;
    }

    public long addAccount(Connection c, String name, long delta) throws SQLException {
        lockAccount(c, name);
        try (PreparedStatement ps = c.prepareStatement(
                "UPDATE ft_accounts SET balance = balance + ? WHERE name = ? RETURNING balance")) {
            ps.setLong(1, delta);
            ps.setString(2, name);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        }
    }

    public Map<String, Long> loadAccounts(Connection c) throws SQLException {
        Map<String, Long> out = new HashMap<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT name, balance FROM ft_accounts")) {
            while (rs.next()) out.put(rs.getString(1), rs.getLong(2));
        }
        return out;
    }

    // ---------------------------------------------------------------- history

    public record Tx(long ts, String serverId, String player, String playerName, String source, String item, long qty,
                     long amount, long tax, long stockAfter) {}

    public void insertTx(Connection c, Tx t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ft_transactions (ts, server_id, player, player_name, "
                + "source, item, qty, amount, tax, stock_after) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)")) {
            ps.setLong(1, t.ts());
            ps.setString(2, t.serverId());
            ps.setString(3, t.player());
            ps.setString(4, t.playerName());
            ps.setString(5, t.source());
            ps.setString(6, t.item());
            ps.setLong(7, t.qty());
            ps.setLong(8, t.amount());
            ps.setLong(9, t.tax());
            ps.setLong(10, t.stockAfter());
            ps.executeUpdate();
        }
    }

    public List<Tx> recentTx(Connection c, String item, int limit) throws SQLException {
        String sql = "SELECT ts, server_id, player, player_name, source, item, qty, amount, tax, stock_after FROM ft_transactions"
                + (item == null ? "" : " WHERE item = ?") + " ORDER BY id DESC LIMIT ?";
        List<Tx> out = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            int i = 1;
            if (item != null) ps.setString(i++, item);
            ps.setInt(i, limit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    out.add(new Tx(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5),
                            rs.getString(6), rs.getLong(7), rs.getLong(8), rs.getLong(9), rs.getLong(10)));
                }
            }
        }
        return out;
    }

    public int pruneHistory(Connection c, long olderThanMs) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM ft_transactions WHERE ts < ?")) {
            ps.setLong(1, olderThanMs);
            return ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- backoff

    public BackoffTracker.State lockBackoff(Connection c, String player, String item) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT last_sell, last_rt, round_trips, lvl FROM ft_backoff "
                + "WHERE player = ? AND item = ?" + d.forUpdate)) {
            ps.setString(1, player);
            ps.setString(2, item);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return new BackoffTracker.State(rs.getLong(1), rs.getLong(2), rs.getInt(3), rs.getInt(4));
            }
        }
        return new BackoffTracker.State();
    }

    public void saveBackoff(Connection c, String player, String item, BackoffTracker.State s) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ft_backoff (player, item, last_sell, last_rt, "
                + "round_trips, lvl) VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (player, item) DO UPDATE SET "
                + "last_sell = excluded.last_sell, last_rt = excluded.last_rt, round_trips = excluded.round_trips, "
                + "lvl = excluded.lvl")) {
            ps.setString(1, player);
            ps.setString(2, item);
            ps.setLong(3, s.lastSellMs);
            ps.setLong(4, s.lastRoundTripMs);
            ps.setInt(5, s.roundTrips);
            ps.setInt(6, s.level);
            ps.executeUpdate();
        }
    }

    // ---------------------------------------------------------------- triggers

    public record Trigger(String id, String account, String op, long threshold, long cost, String mode, long cooldownMs,
                          String command, int fired, long lastFired, boolean armed, boolean enabled) {
        public boolean test(long balance) {
            return switch (op) {
                case ">=" -> balance >= threshold;
                case ">" -> balance > threshold;
                case "<=" -> balance <= threshold;
                case "<" -> balance < threshold;
                case "=" -> balance == threshold;
                default -> false;
            };
        }
    }

    public List<Trigger> loadTriggers(Connection c) throws SQLException {
        List<Trigger> out = new ArrayList<>();
        try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT id, account, op, threshold, cost, "
                + "mode, cooldown_ms, command, fired, last_fired, armed, enabled FROM ft_triggers ORDER BY id")) {
            while (rs.next()) {
                out.add(new Trigger(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5),
                        rs.getString(6), rs.getLong(7), rs.getString(8), rs.getInt(9), rs.getLong(10), rs.getInt(11) != 0,
                        rs.getInt(12) != 0));
            }
        }
        return out;
    }

    public void upsertTrigger(Connection c, Trigger t) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("INSERT INTO ft_triggers (id, account, op, threshold, cost, mode, "
                + "cooldown_ms, command, fired, last_fired, armed, enabled) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) "
                + "ON CONFLICT (id) DO UPDATE SET account = excluded.account, op = excluded.op, threshold = excluded.threshold, "
                + "cost = excluded.cost, mode = excluded.mode, cooldown_ms = excluded.cooldown_ms, command = excluded.command, "
                + "fired = excluded.fired, last_fired = excluded.last_fired, armed = excluded.armed, enabled = excluded.enabled")) {
            ps.setString(1, t.id());
            ps.setString(2, t.account());
            ps.setString(3, t.op());
            ps.setLong(4, t.threshold());
            ps.setLong(5, t.cost());
            ps.setString(6, t.mode());
            ps.setLong(7, t.cooldownMs());
            ps.setString(8, t.command());
            ps.setInt(9, t.fired());
            ps.setLong(10, t.lastFired());
            ps.setInt(11, t.armed() ? 1 : 0);
            ps.setInt(12, t.enabled() ? 1 : 0);
            ps.executeUpdate();
        }
    }

    /** Locks a trigger row so only one server fires it. Null when gone. */
    public Trigger lockTrigger(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT id, account, op, threshold, cost, mode, cooldown_ms, command, "
                + "fired, last_fired, armed, enabled FROM ft_triggers WHERE id = ?" + d.forUpdate)) {
            ps.setString(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                return new Trigger(rs.getString(1), rs.getString(2), rs.getString(3), rs.getLong(4), rs.getLong(5),
                        rs.getString(6), rs.getLong(7), rs.getString(8), rs.getInt(9), rs.getLong(10), rs.getInt(11) != 0,
                        rs.getInt(12) != 0);
            }
        }
    }

    public boolean deleteTrigger(Connection c, String id) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("DELETE FROM ft_triggers WHERE id = ?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        }
    }
}
