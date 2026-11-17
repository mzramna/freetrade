package dev.freetrade.valuation;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

import dev.freetrade.FreeTrade;
import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.config.ValueTable;
import dev.freetrade.currency.Currency;
import dev.freetrade.db.MarketStore;
import dev.freetrade.market.Market;
import dev.freetrade.market.Money;
import dev.freetrade.util.ItemKeys;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;

/**
 * Minecraft side of valuation: reads values.json, every recipe the server knows (crafting, smelting,
 * stonecutting, smithing and mod machines that expose ingredients/results), solves base values and stores them.
 */
public final class Valuation {
    private Valuation() {}

    /** Inputs read on the server thread. */
    public record Inputs(Map<String, Double> declared, Map<String, Long> reference, Map<String, Long> initial,
                         Set<String> excluded, Set<String> currency, List<String> allItems, List<RecipeEdge> recipes) {}

    // ------------------------------------------------------------------ inputs

    /** Expands "#tag" keys to items; exact ids always win over tags. */
    static <V> Map<String, V> resolve(Map<String, V> raw) {
        Map<String, V> fromTags = new HashMap<>();
        Map<String, V> exact = new HashMap<>();
        for (var e : raw.entrySet()) {
            String k = e.getKey();
            if (k.startsWith("#")) {
                ResourceLocation rl = ResourceLocation.tryParse(k.substring(1));
                if (rl == null) continue;
                TagKey<Item> tag = TagKey.create(Registries.ITEM, rl);
                for (var h : BuiltInRegistries.ITEM.getTagOrEmpty(tag)) {
                    fromTags.putIfAbsent(BuiltInRegistries.ITEM.getKey(h.value()).toString(), e.getValue());
                }
            } else {
                exact.put(k, e.getValue());
            }
        }
        fromTags.putAll(exact);
        return fromTags;
    }

    static Set<String> resolveSet(Set<String> raw) {
        Map<String, Boolean> m = new HashMap<>();
        for (String s : raw) m.put(s, Boolean.TRUE);
        return new HashSet<>(resolve(m).keySet());
    }

    public static Inputs gather(MinecraftServer server) {
        ValueTable.Data table = ValueTable.load();
        Currency.rebuild();
        Map<String, Double> declared = new HashMap<>(resolve(table.values()));
        Set<String> currency = Currency.ids();
        for (var d : Currency.denominations()) {
            declared.put(ItemKeys.id(d.item()), d.milli() / (double) Money.SCALE);
        }
        Set<String> excluded = resolveSet(table.excluded());
        List<String> all = new ArrayList<>();
        for (Item i : BuiltInRegistries.ITEM) {
            if (i != Items.AIR) all.add(ItemKeys.id(i));
        }
        return new Inputs(declared, resolve(table.referenceStock()), resolve(table.initialStock()), excluded, currency,
                all, extractRecipes(server));
    }

    public static List<RecipeEdge> extractRecipes(MinecraftServer server) {
        List<RecipeEdge> out = new ArrayList<>();
        var registries = server.registryAccess();
        for (RecipeHolder<?> holder : server.getRecipeManager().getRecipes()) {
            Recipe<?> recipe = holder.value();
            try {
                if (recipe.isSpecial()) continue;
                ItemStack result = recipe.getResultItem(registries);
                if (result == null || result.isEmpty()) continue;
                List<Ingredient> ings = new ArrayList<>();
                if (recipe instanceof SmithingTransformRecipe s) {
                    ings.add(s.template);
                    ings.add(s.base);
                    ings.add(s.addition);
                } else {
                    ings.addAll(recipe.getIngredients());
                }
                Map<List<String>, Integer> slots = new LinkedHashMap<>();
                List<String> remainders = new ArrayList<>();
                for (Ingredient ing : ings) {
                    if (ing == null || ing.isEmpty()) continue;
                    ItemStack[] stacks = ing.getItems();
                    if (stacks.length == 0) continue;
                    List<String> alts = new ArrayList<>();
                    ItemStack remainder = ItemStack.EMPTY;
                    for (ItemStack st : stacks) {
                        String id = ItemKeys.id(st);
                        if (!alts.contains(id)) alts.add(id);
                        if (remainder.isEmpty() && recipe.getType() == RecipeType.CRAFTING) {
                            remainder = st.getCraftingRemainingItem();
                        }
                    }
                    slots.merge(alts, Math.max(1, stacks[0].getCount()), Integer::sum);
                    if (!remainder.isEmpty()) remainders.add(ItemKeys.id(remainder));
                }
                if (slots.isEmpty()) continue;
                List<RecipeEdge.Slot> slotList = new ArrayList<>();
                slots.forEach((alts, n) -> slotList.add(new RecipeEdge.Slot(List.copyOf(alts), n)));
                out.add(new RecipeEdge(holder.id().toString(), ItemKeys.id(result), result.getCount(), slotList,
                        remainders));
            } catch (RuntimeException e) {
                // some mod recipes cannot report static ingredients/results
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ solve / store

    static String encode(ValueSolver.Derivation d) {
        StringBuilder sb = new StringBuilder();
        RecipeEdge r = d.recipe();
        sb.append(r.recipeId()).append('|').append(r.outputCount()).append('|');
        for (int i = 0; i < r.slots().size(); i++) {
            if (i > 0) sb.append(';');
            sb.append(d.chosen().get(i)).append('*').append(r.slots().get(i).count());
        }
        sb.append('|').append(String.join(",", r.remainders()));
        return sb.toString();
    }

    static ValueSolver.Derivation decode(String output, String s) {
        if (s == null || s.isEmpty()) return null;
        try {
            String[] parts = s.split("\\|", -1);
            int outCount = Integer.parseInt(parts[1]);
            List<RecipeEdge.Slot> slots = new ArrayList<>();
            List<String> chosen = new ArrayList<>();
            for (String slot : parts[2].split(";")) {
                int star = slot.lastIndexOf('*');
                String id = slot.substring(0, star);
                chosen.add(id);
                slots.add(new RecipeEdge.Slot(List.of(id), Integer.parseInt(slot.substring(star + 1))));
            }
            List<String> rem = parts.length > 3 && !parts[3].isEmpty() ? List.of(parts[3].split(",")) : List.of();
            return new ValueSolver.Derivation(new RecipeEdge(parts[0], output, outCount, slots, rem), chosen);
        } catch (RuntimeException e) {
            return null;
        }
    }

    public record Outcome(int declared, int derived, int unpriced, Set<String> unpricedItems, long millis) {}

    private static volatile Set<String> lastUnpriced = Set.of();

    public static Set<String> lastUnpriced() {
        return lastUnpriced;
    }

    /**
     * Loads stored values or solves anew. {@code force} always re-solves (/freetrade recalc). Must be given
     * inputs gathered on the server thread; the heavy work may run on any thread.
     */
    public static Outcome apply(Market market, Inputs in, boolean force) throws SQLException {
        long t0 = System.currentTimeMillis();
        MarketStore store = market.store();
        Map<String, MarketStore.StoredValue> stored = force ? Map.of() : market.database().call(store::loadBaseValues);
        Map<String, Double> derived = new HashMap<>();
        Map<String, ValueSolver.Derivation> derivations = new HashMap<>();
        Set<String> blocked = new HashSet<>(in.excluded());
        blocked.addAll(in.currency());
        Set<String> unpriced;
        boolean solved;
        if (stored.isEmpty()) {
            ValueSolver.Result r = ValueSolver.solve(in.declared(), in.recipes(), in.allItems(), blocked,
                    FreeTradeConfig.laborPercent, 64);
            r.values().forEach((k, v) -> {
                if (!in.declared().containsKey(k)) derived.put(k, v);
            });
            derivations.putAll(r.derivations());
            Map<String, MarketStore.StoredValue> save = new HashMap<>();
            in.declared().forEach((k, v) -> save.put(k, new MarketStore.StoredValue(v, "declared", null)));
            derived.forEach((k, v) -> {
                ValueSolver.Derivation d = derivations.get(k);
                save.put(k, new MarketStore.StoredValue(v, "derived", d == null ? null : encode(d)));
            });
            market.database().call(c -> {
                store.replaceBaseValues(c, save);
                return null;
            });
            unpriced = r.unpriced();
            solved = true;
        } else {
            stored.forEach((k, v) -> {
                if (!"derived".equals(v.source()) || in.declared().containsKey(k) || blocked.contains(k)) return;
                derived.put(k, v.value());
                ValueSolver.Derivation d = decode(k, v.derivation());
                if (d != null) derivations.put(k, d);
            });
            unpriced = new HashSet<>();
            for (String id : in.allItems()) {
                if (!blocked.contains(id) && !in.declared().containsKey(id) && !derived.containsKey(id)) unpriced.add(id);
            }
            solved = false;
        }
        Map<String, Double> declared = new HashMap<>(in.declared());
        Market.Snapshot snap = new Market.Snapshot(declared, derived, derivations, in.reference(), in.initial(),
                in.currency());
        market.install(snap, initialStocks(snap, in, blocked));
        lastUnpriced = Set.copyOf(unpriced);
        long ms = System.currentTimeMillis() - t0;
        FreeTrade.LOG.info("Free Trade valuation {}: {} declared, {} derived, {} unpriced ({} recipes, {} ms)",
                solved ? "solved" : "loaded", declared.size() - in.currency().size(), derived.size(), unpriced.size(),
                in.recipes().size(), ms);
        return new Outcome(declared.size(), derived.size(), unpriced.size(), unpriced, ms);
    }

    static Map<String, Long> initialStocks(Market.Snapshot snap, Inputs in, Set<String> blocked) {
        Map<String, Long> out = new HashMap<>();
        Random rnd = new Random();
        Set<String> items = new HashSet<>(snap.declared().keySet());
        items.addAll(snap.derived().keySet());
        items.removeAll(blocked);
        for (String id : items) {
            Long fixed = in.initial().get(id);
            long ref = in.reference().getOrDefault(id, FreeTradeConfig.referenceStock);
            long v = fixed != null ? fixed : switch (FreeTradeConfig.initialStock) {
                case REFERENCE -> ref;
                case ZERO -> 0L;
                case RANDOM -> Math.round(ref * (FreeTradeConfig.randomMin
                        + rnd.nextDouble() * (FreeTradeConfig.randomMax - FreeTradeConfig.randomMin)));
            };
            out.put(id, Math.max(0, v));
        }
        return out;
    }

    /** /freetrade recalc: gather on the server thread, solve off-thread. */
    public static CompletableFuture<Outcome> recalc(MinecraftServer server, Market market) {
        Inputs in = gather(server);
        return CompletableFuture.supplyAsync(() -> {
            try {
                return apply(market, in, true);
            } catch (SQLException e) {
                throw new RuntimeException(e);
            }
        });
    }
}
