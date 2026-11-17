package dev.freetrade.market;

/**
 * Turns the price formula into quotes. The formula gives the value of ONE unit at a given stock level x.
 * Selling n units with stock S pays the units at x = S+1 .. S+n; buying n units with stock S costs the
 * units at x = S .. S-n+1. Selling and buying back the same units therefore cost exactly the same
 * (no round-trip profit) and big trades move the price while they happen (slippage).
 */
public final class PricingEngine {
    /** Hard cap of units summed one by one; larger trades are integrated in chunks. */
    private static final int EXACT_LIMIT = 65_536;

    public record Params(PriceExpression expression, double ceilingUm, double minUnitUm) {
        public Params {
            if (minUnitUm < 0) minUnitUm = 0;
        }
    }

    private volatile Params params;

    public PricingEngine(Params params) {
        this.params = params;
    }

    public Params params() {
        return params;
    }

    public void setParams(Params params) {
        this.params = params;
    }

    /** Value in U.M. (double) of one unit when the stock is x. */
    public double unitValue(double base, double ref, long x) {
        Params p = params;
        if (base <= 0) return 0;
        double xx = Math.max(1, x);
        double v = p.expression().eval(xx, base, Math.max(1, ref));
        if (Double.isNaN(v) || v < 0) v = 0;
        if (p.ceilingUm() >= 0 && v > p.ceilingUm()) v = p.ceilingUm();
        if (Double.isInfinite(v)) v = Long.MAX_VALUE / (double) Money.SCALE;
        return Math.max(v, p.minUnitUm());
    }

    private double sum(double base, double ref, long fromX, long toX) {
        // inclusive range, fromX <= toX
        long n = toX - fromX + 1;
        if (n <= 0) return 0;
        if (n <= EXACT_LIMIT) {
            double s = 0;
            for (long x = fromX; x <= toX; x++) s += unitValue(base, ref, x);
            return s;
        }
        // Midpoint integration over chunks: plenty accurate for smooth formulas at that scale.
        long chunk = n / EXACT_LIMIT + 1;
        double s = 0;
        for (long x = fromX; x <= toX; x += chunk) {
            long end = Math.min(toX, x + chunk - 1);
            long count = end - x + 1;
            s += unitValue(base, ref, x + count / 2) * count;
        }
        return s;
    }

    /** Milli-U.M. paid by the association for n units when stock is S (rounded down). */
    public long sellValue(double base, double ref, long stock, long n) {
        if (n <= 0) return 0;
        return (long) Math.floor(Math.min(sum(base, ref, stock + 1, stock + n) * Money.SCALE, Long.MAX_VALUE));
    }

    /** Milli-U.M. charged by the association for n units when stock is S (rounded up). -1 if not enough stock. */
    public long buyCost(double base, double ref, long stock, long n) {
        if (n <= 0) return 0;
        if (n > stock) return -1;
        return (long) Math.ceil(Math.min(sum(base, ref, stock - n + 1, stock) * Money.SCALE, Long.MAX_VALUE));
    }

    /** Most units that can be bought with a budget (milli). */
    public long maxAffordable(double base, double ref, long stock, long budgetMilli, double multiplier) {
        if (budgetMilli <= 0 || stock <= 0) return 0;
        long lo = 0, hi = stock;
        while (lo < hi) {
            long mid = (lo + hi + 1) >>> 1;
            long cost = applyMultiplier(buyCost(base, ref, stock, mid), multiplier);
            if (cost >= 0 && cost <= budgetMilli) lo = mid;
            else hi = mid - 1;
        }
        return lo;
    }

    public static long applyMultiplier(long milli, double multiplier) {
        if (milli < 0) return milli;
        if (multiplier == 1.0) return milli;
        return (long) Math.ceil(Math.min(milli * multiplier, Long.MAX_VALUE));
    }
}
