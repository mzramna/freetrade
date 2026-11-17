package dev.freetrade.config;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import dev.freetrade.FreeTrade;
import net.neoforged.fml.loading.FMLPaths;

/**
 * config/freetrade/values.json: base values (U.M.) of items, per-item reference and initial stock and items
 * excluded from the market. Keys are item ids or item tags prefixed with '#'. Exact item ids beat tags.
 * Items missing here get values derived from recipes.
 */
public final class ValueTable {
    /**
     * taxes: item id or #tag -> {"sell": "5%", "buy": "2"}. "N%" is a percentage of the trade, a plain number is a
     * fixed amount of U.M. per unit traded. Each one replaces the global tax of that direction for that item.
     */
    public record Data(Map<String, Double> values, Map<String, Long> referenceStock, Map<String, Long> initialStock,
                       Set<String> excluded, Map<String, String> sellTaxes, Map<String, String> buyTaxes) {
        public static Data empty() {
            return new Data(Map.of(), Map.of(), Map.of(), Set.of(), Map.of(), Map.of());
        }
    }

    private static volatile Data data = Data.empty();

    private ValueTable() {}

    public static Data get() {
        return data;
    }

    public static Path file() {
        return FMLPaths.CONFIGDIR.get().resolve(FreeTrade.MODID).resolve("values.json");
    }

    public static synchronized Data load() {
        Path f = file();
        try {
            if (!Files.exists(f)) {
                Files.createDirectories(f.getParent());
                try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
                    w.write(defaultJson());
                }
            }
            try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
                data = parse(JsonParser.parseReader(r).getAsJsonObject());
            }
        } catch (Exception e) {
            FreeTrade.LOG.error("Could not read {}: {}. Keeping previous values.", f, e.toString());
        }
        return data;
    }

    static Data parse(JsonObject o) {
        Map<String, Double> values = new LinkedHashMap<>();
        Map<String, Long> ref = new LinkedHashMap<>();
        Map<String, Long> init = new LinkedHashMap<>();
        Set<String> excluded = new LinkedHashSet<>();
        if (o.has("values")) {
            for (var e : o.getAsJsonObject("values").entrySet()) {
                if (e.getKey().startsWith("_")) continue;
                double v = e.getValue().getAsDouble();
                if (v > 0) values.put(e.getKey().trim(), v);
            }
        }
        if (o.has("reference_stock")) {
            for (var e : o.getAsJsonObject("reference_stock").entrySet()) {
                if (!e.getKey().startsWith("_")) ref.put(e.getKey().trim(), Math.max(1, e.getValue().getAsLong()));
            }
        }
        if (o.has("initial_stock")) {
            for (var e : o.getAsJsonObject("initial_stock").entrySet()) {
                if (!e.getKey().startsWith("_")) init.put(e.getKey().trim(), Math.max(0, e.getValue().getAsLong()));
            }
        }
        if (o.has("excluded")) {
            for (JsonElement e : o.getAsJsonArray("excluded")) excluded.add(e.getAsString().trim());
        }
        Map<String, String> sellTax = new LinkedHashMap<>();
        Map<String, String> buyTax = new LinkedHashMap<>();
        if (o.has("taxes")) {
            for (var e : o.getAsJsonObject("taxes").entrySet()) {
                if (e.getKey().startsWith("_") || !e.getValue().isJsonObject()) continue;
                JsonObject t = e.getValue().getAsJsonObject();
                if (t.has("sell")) sellTax.put(e.getKey().trim(), t.get("sell").getAsString());
                if (t.has("buy")) buyTax.put(e.getKey().trim(), t.get("buy").getAsString());
            }
        }
        return new Data(values, ref, init, excluded, sellTax, buyTax);
    }

    /** Persists a single value (used by /freetrade setvalue). */
    public static synchronized void putValue(String key, double value) throws IOException {
        Path f = file();
        JsonObject o;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            o = JsonParser.parseReader(r).getAsJsonObject();
        }
        if (!o.has("values")) o.add("values", new JsonObject());
        if (value <= 0) o.getAsJsonObject("values").remove(key);
        else o.getAsJsonObject("values").addProperty(key, value);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
            gson.toJson(o, w);
        }
        data = parse(o);
    }

    /** Sets (value != null) or removes the sell/buy tax of a key. direction: "sell" or "buy". */
    public static synchronized void putTax(String key, String direction, String value) throws IOException {
        Path f = file();
        JsonObject o;
        try (Reader r = Files.newBufferedReader(f, StandardCharsets.UTF_8)) {
            o = JsonParser.parseReader(r).getAsJsonObject();
        }
        if (!o.has("taxes")) o.add("taxes", new JsonObject());
        JsonObject taxes = o.getAsJsonObject("taxes");
        JsonObject t = taxes.has(key) && taxes.get(key).isJsonObject() ? taxes.getAsJsonObject(key) : new JsonObject();
        if (value == null) t.remove(direction);
        else t.addProperty(direction, value);
        if (t.size() == 0) taxes.remove(key);
        else taxes.add(key, t);
        Gson gson = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
        try (Writer w = Files.newBufferedWriter(f, StandardCharsets.UTF_8)) {
            gson.toJson(o, w);
        }
        data = parse(o);
    }

    private static String defaultJson() {
        JsonObject root = new JsonObject();
        root.addProperty("_comment", "Base values in U.M. (1 U.M. = 1 emerald by default). Keys: item ids or #tags. "
                + "Exact ids beat tags. Undeclared items get values from their recipes. reference_stock: stock where "
                + "the price equals the base value. initial_stock: stock when the association first sees the item. "
                + "excluded: never traded.");
        JsonObject v = new JsonObject();
        Object[][] seed = {
                // wood & plants
                {"#minecraft:logs", 4}, {"#minecraft:saplings", 1}, {"#minecraft:leaves", 0.25}, {"minecraft:bamboo", 0.5},
                {"minecraft:sugar_cane", 1}, {"minecraft:cactus", 1}, {"minecraft:vine", 0.5}, {"minecraft:kelp", 0.25},
                {"#minecraft:flowers", 0.5}, {"minecraft:pumpkin", 2}, {"minecraft:melon_slice", 0.5},
                {"minecraft:wheat", 1}, {"minecraft:wheat_seeds", 0.25}, {"minecraft:carrot", 1}, {"minecraft:potato", 1},
                {"minecraft:beetroot", 1}, {"minecraft:beetroot_seeds", 0.25}, {"minecraft:cocoa_beans", 1},
                {"minecraft:sweet_berries", 0.5}, {"minecraft:glow_berries", 1}, {"minecraft:apple", 2},
                {"minecraft:brown_mushroom", 1}, {"minecraft:red_mushroom", 1}, {"minecraft:nether_wart", 2},
                {"minecraft:chorus_fruit", 2}, {"minecraft:lily_pad", 0.5}, {"minecraft:moss_block", 1},
                // earth & stone
                {"minecraft:dirt", 0.25}, {"minecraft:grass_block", 0.5}, {"minecraft:sand", 0.5}, {"minecraft:red_sand", 0.5},
                {"minecraft:gravel", 0.5}, {"minecraft:clay_ball", 0.5}, {"minecraft:cobblestone", 0.25},
                {"minecraft:cobbled_deepslate", 0.25}, {"minecraft:stone", 0.5}, {"minecraft:deepslate", 0.5},
                {"minecraft:andesite", 0.25}, {"minecraft:diorite", 0.25}, {"minecraft:granite", 0.25},
                {"minecraft:tuff", 0.25}, {"minecraft:calcite", 0.5}, {"minecraft:netherrack", 0.1},
                {"minecraft:soul_sand", 0.5}, {"minecraft:soul_soil", 0.5}, {"minecraft:basalt", 0.25},
                {"minecraft:blackstone", 0.25}, {"minecraft:end_stone", 0.5}, {"minecraft:obsidian", 8},
                {"minecraft:ice", 1}, {"minecraft:snowball", 0.1}, {"minecraft:magma_block", 2},
                {"minecraft:dripstone_block", 0.5}, {"minecraft:pointed_dripstone", 0.25}, {"minecraft:mud", 0.25},
                {"minecraft:prismarine_shard", 2}, {"minecraft:prismarine_crystals", 3},
                // ores & minerals
                {"minecraft:coal", 2}, {"minecraft:charcoal", 2}, {"minecraft:raw_iron", 8}, {"minecraft:iron_ingot", 8},
                {"minecraft:raw_copper", 3}, {"minecraft:copper_ingot", 3}, {"minecraft:raw_gold", 12},
                {"minecraft:gold_ingot", 12}, {"minecraft:redstone", 2}, {"minecraft:lapis_lazuli", 4},
                {"minecraft:diamond", 64}, {"minecraft:quartz", 3}, {"minecraft:amethyst_shard", 4},
                {"minecraft:glowstone_dust", 3}, {"minecraft:ancient_debris", 256}, {"minecraft:netherite_scrap", 256},
                {"minecraft:flint", 0.5},
                // mob drops
                {"minecraft:string", 1}, {"minecraft:bone", 1}, {"minecraft:rotten_flesh", 0.25},
                {"minecraft:gunpowder", 3}, {"minecraft:spider_eye", 1}, {"minecraft:ender_pearl", 16},
                {"minecraft:blaze_rod", 16}, {"minecraft:slime_ball", 4}, {"minecraft:leather", 2},
                {"minecraft:feather", 0.5}, {"minecraft:egg", 0.5}, {"minecraft:ghast_tear", 32},
                {"minecraft:magma_cream", 8}, {"minecraft:phantom_membrane", 8}, {"minecraft:rabbit_hide", 1},
                {"minecraft:rabbit_foot", 8}, {"minecraft:ink_sac", 1}, {"minecraft:glow_ink_sac", 3},
                {"minecraft:honeycomb", 2}, {"minecraft:shulker_shell", 64}, {"minecraft:nautilus_shell", 16},
                {"minecraft:heart_of_the_sea", 256}, {"minecraft:nether_star", 1024}, {"minecraft:dragon_breath", 32},
                {"minecraft:turtle_scute", 16}, {"minecraft:armadillo_scute", 8}, {"minecraft:breeze_rod", 16},
                {"minecraft:echo_shard", 32}, {"minecraft:totem_of_undying", 256}, {"minecraft:trident", 256},
                {"minecraft:wither_skeleton_skull", 128}, {"minecraft:experience_bottle", 4},
                {"#minecraft:wool", 4}, {"minecraft:white_wool", 4},
                // food
                {"minecraft:beef", 2}, {"minecraft:porkchop", 2}, {"minecraft:chicken", 1.5}, {"minecraft:mutton", 1.5},
                {"minecraft:rabbit", 1.5}, {"minecraft:cod", 1}, {"minecraft:salmon", 1.5}, {"minecraft:tropical_fish", 2},
                {"minecraft:pufferfish", 2}, {"minecraft:milk_bucket", 9}, {"minecraft:honey_bottle", 3},
                {"minecraft:water_bucket", 8}, {"minecraft:lava_bucket", 10},
                // misc
                {"minecraft:name_tag", 16}, {"minecraft:saddle", 16}, {"minecraft:enchanted_golden_apple", 512},
                {"minecraft:music_disc_13", 32}, {"minecraft:goat_horn", 16}, {"minecraft:sniffer_egg", 64},
        };
        for (Object[] s : seed) v.addProperty((String) s[0], ((Number) s[1]).doubleValue());
        root.add("values", v);
        JsonObject ref = new JsonObject();
        ref.addProperty("_comment", "item id or #tag -> reference stock (default from freetrade-server.toml)");
        ref.addProperty("minecraft:diamond", 200);
        ref.addProperty("minecraft:nether_star", 10);
        ref.addProperty("minecraft:netherite_scrap", 50);
        root.add("reference_stock", ref);
        JsonObject init = new JsonObject();
        init.addProperty("_comment", "item id or #tag -> starting stock (overrides initial_stock mode)");
        root.add("initial_stock", init);
        JsonArray ex = new JsonArray();
        for (String s : new String[]{"minecraft:bedrock", "minecraft:barrier", "minecraft:command_block",
                "minecraft:chain_command_block", "minecraft:repeating_command_block", "minecraft:command_block_minecart",
                "minecraft:structure_block", "minecraft:structure_void", "minecraft:jigsaw", "minecraft:light",
                "minecraft:debug_stick", "minecraft:knowledge_book", "minecraft:spawner", "minecraft:trial_spawner",
                "minecraft:end_portal_frame", "minecraft:reinforced_deepslate", "minecraft:written_book",
                "minecraft:filled_map", "#c:hidden_from_recipe_viewers"}) {
            ex.add(s);
        }
        root.add("excluded", ex);
        JsonObject taxes = new JsonObject();
        taxes.addProperty("_comment", "item id or #tag -> {\"sell\": \"5%\", \"buy\": \"2\"}: N% = percent of the trade, "
                + "a plain number = fixed U.M. per unit. Replaces the global tax for that item and direction.");
        root.add("taxes", taxes);
        return new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root);
    }
}
