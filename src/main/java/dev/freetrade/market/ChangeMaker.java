package dev.freetrade.market;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** Greedy change in the largest denominations first. Denomination values are in whole U.M. */
public final class ChangeMaker {
    public record Denomination<T>(T key, long value) {}

    public record Piece<T>(T key, long count) {}

    public record Result<T>(List<Piece<T>> pieces, long remainder) {}

    private ChangeMaker() {}

    public static <T> Result<T> make(long amount, List<Denomination<T>> denominations) {
        List<Denomination<T>> sorted = new ArrayList<>(denominations);
        sorted.removeIf(d -> d.value() <= 0);
        sorted.sort(Comparator.comparingLong((Denomination<T> d) -> d.value()).reversed());
        List<Piece<T>> out = new ArrayList<>();
        long left = Math.max(0, amount);
        for (Denomination<T> d : sorted) {
            long c = left / d.value();
            if (c > 0) {
                out.add(new Piece<>(d.key(), c));
                left -= c * d.value();
            }
        }
        return new Result<>(out, left);
    }
}
