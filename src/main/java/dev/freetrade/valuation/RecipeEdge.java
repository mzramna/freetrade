package dev.freetrade.valuation;

import java.util.List;

/**
 * A recipe reduced to what valuation needs: an output with its count, ingredient slots (each slot accepts
 * any one of its alternatives, e.g. a tag) and items handed back after crafting (e.g. empty buckets).
 */
public record RecipeEdge(String recipeId, String output, int outputCount, List<Slot> slots, List<String> remainders) {
    public record Slot(List<String> alternatives, int count) {}
}
