package dev.freetrade.market;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import org.junit.jupiter.api.Test;

class PricingEngineTest {
    private static PricingEngine engine(String expr, double ceiling) {
        return new PricingEngine(new PricingEngine.Params(PriceExpression.parse(expr), ceiling, 0.001));
    }

    @Test
    void roundTripCostsTheSame() {
        PricingEngine e = engine("b * (r / x)", -1);
        long sell = e.sellValue(4, 1000, 1000, 500);
        long buy = e.buyCost(4, 1000, 1500, 500);
        assertTrue(Math.abs(sell - buy) <= 1, sell + " vs " + buy);
    }

    @Test
    void slippageLowersPayoutOfBigSales() {
        PricingEngine e = engine("b * (r / x)", -1);
        long one = e.sellValue(1, 100, 100, 1);
        long thousand = e.sellValue(1, 100, 100, 1000);
        assertTrue(thousand < one * 1000);
    }

    @Test
    void halfStockDoublesPrice() {
        PricingEngine e = engine("b * (r / x)", -1);
        assertEquals(2 * e.unitValue(4, 1000, 1000), e.unitValue(4, 1000, 500), 1e-9);
    }

    @Test
    void ceilingCaps() {
        PricingEngine e = engine("b * (r / x)^2", 10);
        assertEquals(10, e.unitValue(4, 1000, 1), 1e-9);
    }

    @Test
    void buyingMoreThanStockIsRefused() {
        assertEquals(-1, engine("b", -1).buyCost(1, 1, 5, 6));
    }

    @Test
    void roundHalfUp() {
        assertEquals(2000, Money.roundHalfUp(1500));
        assertEquals(1000, Money.roundHalfUp(1499));
        assertEquals(5000, Money.roundHalfUp(4500));
        assertEquals(4000, Money.roundHalfUp(4499));
        assertEquals(0, Money.roundHalfUp(499));
    }

    @Test
    void maxAffordable() {
        PricingEngine e = engine("b", -1);
        assertEquals(10, e.maxAffordable(1, 1, 100, Money.ofUnits(10), 1.0));
        assertEquals(5, e.maxAffordable(1, 1, 100, Money.ofUnits(10), 2.0));
        assertEquals(3, e.maxAffordable(1, 1, 3, Money.ofUnits(10), 1.0));
    }

    @Test
    void hugeTradesStayFinite() {
        PricingEngine e = engine("b * (r / x)", -1);
        long v = e.sellValue(1, 1000, 0, 10_000_000);
        assertTrue(v > 0 && v < Long.MAX_VALUE);
    }

    @Test
    void changeMaker() {
        var r = ChangeMaker.make(23, List.of(new ChangeMaker.Denomination<>("emerald", 1),
                new ChangeMaker.Denomination<>("block", 9)));
        assertEquals(List.of(new ChangeMaker.Piece<>("block", 2), new ChangeMaker.Piece<>("emerald", 5)), r.pieces());
        assertEquals(0, r.remainder());
        var r2 = ChangeMaker.make(7, List.of(new ChangeMaker.Denomination<>("five", 5)));
        assertEquals(2, r2.remainder());
    }

    @Test
    void moneyFormat() {
        assertEquals("1.5", Money.format(1500));
        assertEquals("2", Money.format(2000));
        assertEquals(2, Money.ceilUnits(1001));
        assertEquals(1, Money.floorUnits(1999));
    }

    @Test
    void backoffGrowsAndDecays() {
        var cfg = new BackoffTracker.Settings(true, 2, 1000, 10, 10);
        var s = new BackoffTracker.State();
        BackoffTracker.onSell(s, 100);
        assertEquals(1.0, BackoffTracker.onBuy(s, cfg, 200), 1e-9);
        BackoffTracker.onSell(s, 300);
        assertEquals(1.1, BackoffTracker.onBuy(s, cfg, 400), 1e-9);
        BackoffTracker.onSell(s, 500);
        assertEquals(1.2, BackoffTracker.onBuy(s, cfg, 600), 1e-9);
        // two idle windows later: level drops by 2
        BackoffTracker.decay(s, cfg, 600 + 2500);
        assertEquals(0, s.level);
        assertEquals(1.0, BackoffTracker.multiplier(s, cfg), 1e-9);
    }
}
