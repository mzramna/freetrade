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
        var r = m.sell(CTX, "oak_log", 1000, 1.0).get();
        assertTrue(r.ok());
        assertEquals(2000, m.stock("oak_log"));
        assertTrue(m.unitPrice("oak_planks") < plank * 0.6, "planks follow logs");
        assertTrue(m.unitPrice("stick") < stick * 0.6, "sticks follow planks");
    }

    @Test
    void sticksAreTradedInLotsOfTwo() throws Exception {
        Market m = market(-1, 0);
        // stick = 0.5 U.M. -> lots of 2: never a single stick
        assertEquals(2, m.lotSize("stick"));
        assertEquals(Market.Error.TOO_FEW, m.sell(CTX, "stick", 1, 1.0).get().error());
        var r = m.sell(CTX, "stick", 5, 1.0).get();
        assertTrue(r.ok());
        assertEquals(4, r.units(), "only whole lots are taken, the 5th stick goes back");
        assertEquals(0, r.payoutMilli() % Money.SCALE, "payout is whole U.M.");
        assertEquals(Money.ofUnits(2), r.payoutMilli(), "4 sticks ~ 1.998 U.M. -> 2");
        var b = m.buy(new Market.TradeContext("p9", "Z", "test"), "stick", 1, Long.MAX_VALUE, true).get();
        assertTrue(b.ok());
        assertEquals(2, b.units(), "buying 1 stick delivers a whole lot of 2");
        assertEquals(0, b.costMilli() % Money.SCALE);
    }

    @Test
    void realTotalThenHalfUpRounding() throws Exception {
        // flat formula: price = b exactly, so totals are easy to check
        Market m = new Market(db, new PricingEngine.Params(PriceExpression.parse("b"), -1, 0.001), settings(-1, 0));
        m.install(new Market.Snapshot(Map.of("gem", 1.5, "dust", 1.4), Map.of(), Map.of(), Map.of(), Map.of(), Set.of()),
                Map.of("gem", 1000L, "dust", 1000L));
        assertEquals(Money.ofUnits(2), m.quoteBuy("gem", 1, 1.0), "1.5 -> 2");
        assertEquals(Money.ofUnits(5), m.quoteBuy("gem", 3, 1.0), "3 x 1.5 = 4.5 -> 5 (not 3 x 2 = 6)");
        assertEquals(Money.ofUnits(1), m.quoteBuy("dust", 1, 1.0), "1.4 -> 1");
        assertEquals(Money.ofUnits(3), m.quoteBuy("dust", 2, 1.0), "2.8 -> 3");
        var b = m.buy(new Market.TradeContext("q", "Q", "test"), "gem", 3, Long.MAX_VALUE, true).get();
        assertEquals(Money.ofUnits(5), b.costMilli());
        var s = m.sell(new Market.TradeContext("q2", "Q2", "test"), "dust", 2, 1.0).get();
        assertEquals(Money.ofUnits(3), s.payoutMilli(), "selling 2 x 1.4 = 2.8 -> 3");
    }

    @Test
    void roundingAbuseRaisesBackoff() throws Exception {
        Market m = new Market(db, new PricingEngine.Params(PriceExpression.parse("b"), -1, 0.001), settings(-1, 0));
        m.install(new Market.Snapshot(Map.of("gem", 1.5), Map.of(), Map.of(), Map.of(), Map.of(), Set.of()),
                Map.of("gem", 1000L));
        Market.TradeContext abuser = new Market.TradeContext("abuser", "A", "test");
        // selling one by one gains 0.5 each time; after the threshold the payout shrinks
        long first = m.sell(abuser, "gem", 1, 1.0).get().payoutMilli();
        assertEquals(Money.ofUnits(2), first);
        long last = first;
        for (int i = 0; i < 6; i++) last = m.sell(abuser, "gem", 1, 1.0).get().payoutMilli();
        assertTrue(last < first, "abuse penalised: " + first + " -> " + last);
        // another player is not affected
        assertEquals(Money.ofUnits(2), m.sell(CTX, "gem", 1, 1.0).get().payoutMilli());
    }

    @Test
    void finiteTreasuryAndTax() throws Exception {
        Market m = market(10, 10);
        var r = m.sell(CTX, "oak_log", 100, 1.0).get();
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
        m.sell(p2, "stick", 10, 1.0).get();
        long c1 = m.buy(p2, "stick", 10, Long.MAX_VALUE, true).get().costMilli();
        m.sell(p2, "stick", 10, 1.0).get();
        long c2 = m.buy(p2, "stick", 10, Long.MAX_VALUE, true).get().costMilli();
        assertTrue(c2 > c1 * 1.4, c1 + " -> " + c2);
    }

    @Test
    void accountAdjustRequiresFunds() throws Exception {
        Market m = market(-1, 50);
        m.sell(CTX, "oak_log", 10, 1.0).get();
        long tax = m.account(MarketStore.TAX);
        assertTrue(tax > 0);
        assertNull(m.adjustAccount(CTX, MarketStore.TAX, -(tax + 1), true).get());
        assertEquals(0L, m.adjustAccount(CTX, MarketStore.TAX, -tax, true).get());
    }

    @Test
    void forcedPriceAndStockAdjust() throws Exception {
        Market m = market(-1, 0);
        // forced price replaces the formula, also for an item that had no value at all
        assertFalse(m.isTradeable("mystery"));
        m.forcePrice("mystery", 2.5).get();
        assertTrue(m.isTradeable("mystery"));
        m.adjustStock("mystery", 10).get();
        assertEquals(10, m.stock("mystery"));
        assertEquals(Money.ofUnits(8), m.quoteBuy("mystery", 3, 1.0), "3 x 2.5 = 7.5 -> 8, no slippage");
        // a forced price below 1 U.M. trades in lots
        m.forcePrice("oak_log", 0.25).get();
        assertEquals(4, m.lotSize("oak_log"));
        // clearing goes back to the formula
        m.forcePrice("oak_log", 0).get();
        assertNull(m.forcedPrice("oak_log"));
        assertEquals(1, m.lotSize("oak_log"));
        // stock can be removed but never goes below zero
        assertEquals(0L, m.adjustStock("mystery", -50).get());
        // forced prices are stored in the database
        db.close();
        db = Database.sqlite(dir.resolve("t.db").toString());
        Market again = market(-1, 0);
        assertEquals(2.5, again.forcedPrice("mystery"), 1e-9);
    }

    @Test
    void stockSurvivesReopen() throws Exception {
        Market m = market(-1, 0);
        m.sell(CTX, "oak_log", 5, 1.0).get();
        db.close();
        db = Database.sqlite(dir.resolve("t.db").toString());
        Market again = market(-1, 0);
        assertEquals(1005, again.stock("oak_log"));
        assertEquals(1, db.call(c -> again.store().recentTx(c, "oak_log", 10)).size());
    }
}
