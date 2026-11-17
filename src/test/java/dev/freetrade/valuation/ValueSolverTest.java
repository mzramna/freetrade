package dev.freetrade.valuation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

class ValueSolverTest {
    private static RecipeEdge.Slot slot(int count, String... alts) {
        return new RecipeEdge.Slot(List.of(alts), count);
    }

    @Test
    void woodPlankStick() {
        var planks = new RecipeEdge("planks", "oak_planks", 4, List.of(slot(1, "oak_log")), List.of());
        var sticks = new RecipeEdge("sticks", "stick", 4, List.of(slot(2, "oak_planks", "birch_planks")), List.of());
        var r = ValueSolver.solve(Map.of("oak_log", 4.0), List.of(planks, sticks),
                List.of("oak_log", "oak_planks", "stick", "diamond"), Set.of(), 0, 50);
        assertEquals(1.0, r.values().get("oak_planks"), 1e-9);
        assertEquals(0.5, r.values().get("stick"), 1e-9);
        assertEquals(Set.of("diamond"), r.unpriced());
    }

    @Test
    void cyclesTakeCheapestAndLabor() {
        var block = new RecipeEdge("block", "iron_block", 1, List.of(slot(9, "iron_ingot")), List.of());
        var ingot = new RecipeEdge("ingot", "iron_ingot", 9, List.of(slot(1, "iron_block")), List.of());
        var nugget = new RecipeEdge("nugget", "iron_nugget", 9, List.of(slot(1, "iron_ingot")), List.of());
        var fromNuggets = new RecipeEdge("ingot2", "iron_ingot", 1, List.of(slot(9, "iron_nugget")), List.of());
        var r = ValueSolver.solve(Map.of("iron_ingot", 9.0), List.of(block, ingot, nugget, fromNuggets),
                List.of("iron_ingot", "iron_block", "iron_nugget"), Set.of(), 10, 50);
        assertEquals(9.0, r.values().get("iron_ingot"), 1e-9); // declared, never overwritten
        assertEquals(89.1, r.values().get("iron_block"), 1e-9);
        assertEquals(1.1, r.values().get("iron_nugget"), 1e-9);
    }

    @Test
    void cheapestRecipeWinsAndRemaindersSubtract() {
        var a = new RecipeEdge("a", "cake", 1, List.of(slot(3, "milk_bucket"), slot(1, "egg")), List.of("bucket", "bucket", "bucket"));
        var r = ValueSolver.solve(Map.of("milk_bucket", 5.0, "bucket", 3.0, "egg", 1.0), List.of(a),
                List.of("cake"), Set.of(), 0, 10);
        assertEquals(7.0, r.values().get("cake"), 1e-9);
    }

    @Test
    void dynamicBaseFollowsIngredients() {
        var planks = new RecipeEdge("planks", "oak_planks", 4, List.of(slot(1, "oak_log")), List.of());
        var r = ValueSolver.solve(Map.of("oak_log", 4.0), List.of(planks), List.of("oak_planks"), Set.of(), 0, 10);
        double cheapLogs = ValueSolver.dynamicBase(r.derivations().get("oak_planks"), id -> 2.0, 0);
        assertEquals(0.5, cheapLogs, 1e-9);
    }
}
