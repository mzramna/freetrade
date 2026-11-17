package dev.freetrade.market;

import java.util.Locale;

/**
 * Tax of one item in one direction (sell or buy). Written as "5%" (a percentage of the trade value) or as "2"
 * (a fixed amount of U.M. added per unit traded). It replaces the global tax percentage for that item.
 */
public record TaxRule(boolean percent, double value) {
    public static TaxRule parse(String raw) {
        if (raw == null) throw new IllegalArgumentException("empty tax");
        String s = raw.trim().replace(',', '.');
        boolean pct = s.endsWith("%");
        if (pct) s = s.substring(0, s.length() - 1).trim();
        double v;
        try {
            v = Double.parseDouble(s);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid tax '" + raw + "': use a number like 2 (U.M. per unit) or 5% (percent)");
        }
        if (v < 0 || Double.isNaN(v) || Double.isInfinite(v) || (pct && v > 1000)) {
            throw new IllegalArgumentException("Invalid tax '" + raw + "'");
        }
        return new TaxRule(pct, v);
    }

    /** Tax in milli-U.M. for a trade of {@code units} worth {@code amountMilli}. */
    public long apply(long amountMilli, long units) {
        if (value <= 0 || units <= 0) return 0;
        if (percent) return amountMilli <= 0 ? 0 : (long) Math.floor(amountMilli * value / 100.0);
        return Math.round(value * Money.SCALE * units);
    }

    @Override
    public String toString() {
        String n = value == Math.rint(value) ? Long.toString((long) value) : String.format(Locale.ROOT, "%s", value);
        return percent ? n + "%" : n;
    }
}
