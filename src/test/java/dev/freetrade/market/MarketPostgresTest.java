package dev.freetrade.market;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.freetrade.db.Database;
import dev.freetrade.db.MarketStore;

/**
 * Runs only when FT_PG_URL is set (e.g. jdbc:postgresql://127.0.0.1:54329/freetrade, user from FT_PG_USER).
 * Two Market instances on the same database play the role of two servers sharing one economy.
 */
class MarketPostgresTest {
    private static final String URL = System.getenv("FT_PG_URL");
    private static final String USER = System.getenv().getOrDefault("FT_PG_USER", "postgres");
    private static final String PASS = System.getenv().getOrDefault("FT_PG_PASSWORD", "");

    private Database dbA, dbB;

    private static Market market(Database db, String serverId) throws Exception {
        Market m = new Market(db, new PricingEngine.Params(PriceExpression.parse("b * (r / x)"), -1, 0.001),
                new Market.Settings(1000, true, 0, 10, 0, -1, new BackoffTracker.Settings(true, 2, 60_000, 50, 10), serverId));
        m.install(new Market.Snapshot(Map.of("oak_log", 4.0), Map.of(), Map.of(), Map.of(), Map.of(), Set.of()),
                Map.of("oak_log", 1000L));
        return m;
    }

    @BeforeEach
    void setUp() throws Exception {
        Assumptions.assumeTrue(URL != null && !URL.isBlank(), "FT_PG_URL not set");
        dbA = Database.postgres(URL, USER, PASS);
        dbA.call(c -> {
            try (var s = c.createStatement()) {
                s.execute("DROP TABLE IF EXISTS ft_meta, ft_stock, ft_base_values, ft_accounts, ft_transactions, ft_backoff, ft_triggers");
            }
            return null;
        });
        dbB = Database.postgres(URL, USER, PASS);
    }

    @AfterEach
    void tearDown() {
        if (dbA != null) dbA.close();
        if (dbB != null) dbB.close();
    }

    @Test
    void twoServersShareStockConsistently() throws Exception {
        Market a = market(dbA, "server-a");
        Market b = market(dbB, "server-b");
        List<CompletableFuture<Market.SellResult>> sells = new ArrayList<>();
        for (int i = 0; i < 50; i++) {
            sells.add(a.sell(new Market.TradeContext("pa", "A", "test"), "oak_log", 10, 1.0, false));
            sells.add(b.sell(new Market.TradeContext("pb", "B", "test"), "oak_log", 10, 1.0, false));
        }
        long units = 0, tax = 0;
        for (var f : sells) {
            var r = f.get();
            assertTrue(r.ok(), r.error().toString());
            units += r.units();
            tax += r.taxMilli();
        }
        assertEquals(1000, units);
        // both servers see the same stock after refresh
        a.refreshAsync().get();
        b.refreshAsync().get();
        assertEquals(2000, a.stock("oak_log"));
        assertEquals(2000, b.stock("oak_log"));
        assertEquals(tax, a.account(MarketStore.TAX));
        // history keeps the origin server
        var hist = dbA.call(c -> a.store().recentTx(c, "oak_log", 200));
        assertEquals(100, hist.size());
        assertTrue(hist.stream().anyMatch(t -> t.serverId().equals("server-a")));
        assertTrue(hist.stream().anyMatch(t -> t.serverId().equals("server-b")));
        // stock_after values are a permutation of 1010..2000: no lost update between servers
        var after = hist.stream().map(MarketStore.Tx::stockAfter).sorted().toList();
        for (int i = 0; i < 100; i++) assertEquals(1010L + 10L * i, after.get(i));
    }

    @Test
    void buyNeverOversellsAcrossServers() throws Exception {
        Market a = market(dbA, "server-a");
        Market b = market(dbB, "server-b");
        List<CompletableFuture<Market.BuyResult>> buys = new ArrayList<>();
        for (int i = 0; i < 30; i++) {
            buys.add(a.buy(new Market.TradeContext("pa" + i, "A", "test"), "oak_log", 50, Long.MAX_VALUE, true));
            buys.add(b.buy(new Market.TradeContext("pb" + i, "B", "test"), "oak_log", 50, Long.MAX_VALUE, true));
        }
        long bought = 0;
        for (var f : buys) {
            var r = f.get();
            if (r.ok()) bought += r.units();
            else assertEquals(Market.Error.NO_STOCK, r.error());
        }
        assertEquals(1000, bought, "exactly the stock is sold, never more");
        a.refreshAsync().get();
        assertEquals(0, a.stock("oak_log"));
    }
}
