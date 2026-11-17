package dev.freetrade.market;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import dev.freetrade.db.Database;
import dev.freetrade.db.MarketStore;
import dev.freetrade.valuation.RecipeEdge;
import dev.freetrade.valuation.ValueSolver;

class MarketSqliteTest {
    private Path dir;
    private Database db;

    private static Market.Settings settings(long treasury, double taxSell) {
        return new Market.Settings(1000, true, 0, taxSell, 0, treasury,
                new BackoffTracker.Settings(true, 2, 60_000, 50, 10), "test");
    }

    private Market market(long treasury, double taxSell) throws Exception {
        Market m = new Market(db, new PricingEngine.Params(PriceExpression.parse("b * (r / x)"), -1, 0.001),
                settings(treasury, taxSell));
        var planks = new RecipeEdge("p", "oak_planks", 4, List.of(new RecipeEdge.Slot(List.of("oak_log"), 1)), List.of());
        var sticks = new RecipeEdge("s", "stick", 4, List.of(new RecipeEdge.Slot(List.of("oak_planks"), 2)), List.of());
        var res = ValueSolver.solve(Map.of("oak_log", 4.0), List.of(planks, sticks), List.of(), Set.of(), 0, 20);
        Map<String, Double> derived = new java.util.HashMap<>(res.values());
        derived.remove("oak_log");
        m.install(new Market.Snapshot(Map.of("oak_log", 4.0), derived, res.derivations(), Map.of(),
                Map.of(), Set.of("emerald")), Map.of("oak_log", 1000L, "oak_planks", 1000L, "stick", 1000L));
        return m;
    }

    @BeforeEach
    void setUp() throws Exception {
        dir = Files.createTempDirectory("ft");
        db = Database.sqlite(dir.resolve("t.db").toString());
    }

    @AfterEach
    void tearDown() {
        db.close();
    }

    private static final Market.TradeContext CTX = new Market.TradeContext("p1", "Steve", "test");

    @Test
    void sellingLogsMakesPlanksAndSticksCheaper() throws Exception {
        Market m = market(-1, 0);
        double plank = m.unitPrice("oak_planks"), stick = m.unitPrice("stick");
        assertEquals(1.0, plank, 0.01);
        assertEquals(0.5, stick, 0.01);
        var r = m.sell(CTX, "oak_log", 1000, 1.0, false).get();
        assertTrue(r.ok());
        assertEquals(2000, m.stock("oak_log"));
        assertTrue(m.unitPrice("oak_planks") < plank * 0.6, "planks follow logs");
        assertTrue(m.unitPrice("stick") < stick * 0.6, "sticks follow planks");
    }

    @Test
    void villagerSellsSticksInLots() throws Exception {
        Market m = market(-1, 0);
        // a stick is worth ~0.5 U.M. but each sold unit lowers the price a bit (slippage), so 1 U.M. needs 3 sticks;
        // of 4 offered sticks only 3 are taken and the 4th goes back to the player
        var r = m.sell(CTX, "stick", 4, 1.0, true).get();
        assertTrue(r.ok());
        assertEquals(3, r.units());
        assertEquals(Money.ofUnits(1), r.payoutMilli());
        var none = m.sell(CTX, "stick", 1, 1.0, true).get();
        assertEquals(Market.Error.TOO_FEW, none.error());
    }

    @Test
    void finiteTreasuryAndTax() throws Exception {
        Market m = market(10, 10);
        var r = m.sell(CTX, "oak_log", 100, 1.0, false).get();
        assertTrue(r.ok());
        assertTrue(r.units() < 100, "treasury limits the sale");
        long paid = r.payoutMilli() + r.taxMilli();
        assertTrue(paid <= Money.ofUnits(10));
        assertEquals(Money.ofUnits(10) - paid, m.account(MarketStore.TREASURY));
        assertEquals(r.taxMilli(), m.account(MarketStore.TAX));
        assertTrue(r.taxMilli() > 0);
    }

    @Test
    void buyRespectsBudgetStockAndBackoff() throws Exception {
        Market m = market(-1, 0);
        var b = m.buy(CTX, "oak_log", 1000, Money.ofUnits(20), false).get();
        assertTrue(b.ok());
        assertTrue(b.costMilli() <= Money.ofUnits(20));
        assertEquals(1000 - b.units(), m.stock("oak_log"));
        var tooMany = m.buy(CTX, "oak_log", 5000, Long.MAX_VALUE, true).get();
        assertEquals(Market.Error.NO_STOCK, tooMany.error());

        // round trips: sell then buy back the same, twice -> second buy back costs 50% more
        Market.TradeContext p2 = new Market.TradeContext("p2", "Alex", "test");
        m.sell(p2, "stick", 10, 1.0, false).get();
        long c1 = m.buy(p2, "stick", 10, Long.MAX_VALUE, true).get().costMilli();
        m.sell(p2, "stick", 10, 1.0, false).get();
        long c2 = m.buy(p2, "stick", 10, Long.MAX_VALUE, true).get().costMilli();
        assertTrue(c2 > c1 * 1.4, c1 + " -> " + c2);
    }

    @Test
    void accountAdjustRequiresFunds() throws Exception {
        Market m = market(-1, 50);
        m.sell(CTX, "oak_log", 10, 1.0, false).get();
        long tax = m.account(MarketStore.TAX);
        assertTrue(tax > 0);
        assertNull(m.adjustAccount(CTX, MarketStore.TAX, -(tax + 1), true).get());
        assertEquals(0L, m.adjustAccount(CTX, MarketStore.TAX, -tax, true).get());
    }

    @Test
    void stockSurvivesReopen() throws Exception {
        Market m = market(-1, 0);
        m.sell(CTX, "oak_log", 5, 1.0, false).get();
        db.close();
        db = Database.sqlite(dir.resolve("t.db").toString());
        Market again = market(-1, 0);
        assertEquals(1005, again.stock("oak_log"));
        assertEquals(1, db.call(c -> again.store().recentTx(c, "oak_log", 10)).size());
    }
}
