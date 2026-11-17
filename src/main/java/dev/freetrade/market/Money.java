package dev.freetrade.market;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Monetary amounts are stored as long milli-U.M. (1 U.M. = 1000). Only whole U.M. change hands as items. */
public final class Money {
    public static final long SCALE = 1000L;

    private Money() {}

    public static long ofUnits(long um) {
        return Math.multiplyExact(um, SCALE);
    }

    public static long ofDouble(double um) {
        if (Double.isNaN(um) || um <= 0) return 0;
        if (um >= Long.MAX_VALUE / (double) SCALE) return Long.MAX_VALUE;
        return Math.round(um * SCALE);
    }

    /** Whole U.M. contained in a milli amount (rounded down). */
    public static long floorUnits(long milli) {
        return Math.floorDiv(milli, SCALE);
    }

    /** Whole U.M. needed to pay a milli amount (rounded up). */
    public static long ceilUnits(long milli) {
        return -Math.floorDiv(-milli, SCALE);
    }

    /** Rounds to whole U.M. half-up: .5 and above goes up, below .5 goes down. Result in milli. */
    public static long roundHalfUp(long milli) {
        return Math.floorDiv(milli + SCALE / 2, SCALE) * SCALE;
    }

    public static String format(long milli) {
        BigDecimal v = BigDecimal.valueOf(milli).divide(BigDecimal.valueOf(SCALE), 3, RoundingMode.DOWN)
                .stripTrailingZeros();
        return v.scale() < 0 ? v.setScale(0).toPlainString() : v.toPlainString();
    }

    public static long saturatingAdd(long a, long b) {
        long r = a + b;
        if (((a ^ r) & (b ^ r)) < 0) return a > 0 ? Long.MAX_VALUE : Long.MIN_VALUE;
        return r;
    }
}
