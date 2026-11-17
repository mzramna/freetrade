package dev.freetrade.valuation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * Derives base values of undeclared items from recipes: the cheapest recipe wins, the cheapest
 * alternative of each ingredient slot wins, and the labor percent is added per crafting step.
 * Iterates to a fixed point, so cycles (ingot <-> block <-> nugget) settle on the cheapest path.
 */
public final class ValueSolver {
    public record Derivation(RecipeEdge recipe, List<String> chosen) {}

    public record Result(Map<String, Double> values, Map<String, Derivation> derivations, Set<String> unpriced) {}

    private ValueSolver() {}

    public static Result solve(Map<String, Double> declared, Collection<RecipeEdge> recipes, Collection<String> allItems,
                               Set<String> excluded, double laborPercent, int maxIterations) {
        Map<String, Double> values = new HashMap<>();
        declared.forEach((k, v) -> {
            if (v != null && v > 0) values.put(k, v);
        });
        Map<String, Derivation> derivations = new HashMap<>();
        double labor = 1.0 + laborPercent / 100.0;
        List<RecipeEdge> usable = new ArrayList<>();
        for (RecipeEdge r : recipes) {
            if (r.outputCount() <= 0 || r.slots().isEmpty()) continue;
            if (declared.containsKey(r.output()) || excluded.contains(r.output())) continue;
            usable.add(r);
        }
        for (int it = 0; it < maxIterations; it++) {
            boolean changed = false;
            for (RecipeEdge r : usable) {
                double cost = 0;
                List<String> chosen = new ArrayList<>(r.slots().size());
                boolean ok = true;
                for (RecipeEdge.Slot slot : r.slots()) {
                    String best = null;
                    double bestV = Double.MAX_VALUE;
                    for (String alt : slot.alternatives()) {
                        if (alt.equals(r.output())) continue;
                        Double v = values.get(alt);
                        if (v != null && v < bestV) {
                            bestV = v;
                            best = alt;
                        }
                    }
                    if (best == null) {
                        ok = false;
                        break;
                    }
                    cost += bestV * slot.count();
                    chosen.add(best);
                }
                if (!ok) continue;
                for (String rem : r.remainders()) {
                    Double v = values.get(rem);
                    if (v != null) cost -= v;
                }
                if (cost <= 0) continue;
                double value = cost * labor / r.outputCount();
                Double cur = values.get(r.output());
                if (cur == null || value < cur * (1 - 1e-9)) {
                    values.put(r.output(), value);
                    derivations.put(r.output(), new Derivation(r, chosen));
                    changed = true;
                }
            }
            if (!changed) break;
        }
        Set<String> unpriced = new HashSet<>();
        for (String item : allItems) {
            if (!values.containsKey(item) && !excluded.contains(item)) unpriced.add(item);
        }
        return new Result(values, derivations, unpriced);
    }

    /**
     * Base value of a derived item from the CURRENT prices of its ingredients (dynamic propagation).
     * {@code currentPrice} returns the current market unit price of an item.
     */
    public static double dynamicBase(Derivation d, ToDoubleFunction<String> currentPrice, double laborPercent) {
        RecipeEdge r = d.recipe();
        double cost = 0;
        for (int i = 0; i < r.slots().size(); i++) {
            cost += currentPrice.applyAsDouble(d.chosen().get(i)) * r.slots().get(i).count();
        }
        for (String rem : r.remainders()) cost -= currentPrice.applyAsDouble(rem);
        if (cost <= 0) return 0;
        return cost * (1.0 + laborPercent / 100.0) / r.outputCount();
    }

    /** Guard set helper for recursive dynamic pricing. */
    public static Set<String> newVisitSet() {
        return new HashSet<>();
    }
}
