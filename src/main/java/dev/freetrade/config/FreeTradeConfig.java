package dev.freetrade.config;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import dev.freetrade.FreeTrade;
import dev.freetrade.market.BackoffTracker;
import dev.freetrade.market.Money;
import dev.freetrade.market.PriceExpression;
import dev.freetrade.market.PricingEngine;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server config: serverconfig/freetrade-server.toml in the world (or defaultconfigs/ in a modpack).
 * Per-item values live in config/freetrade/values.json (see {@link ValueTable}).
 * Values are baked into plain static fields on load/reload so game code never touches the spec.
 */
public final class FreeTradeConfig {
    public static final ModConfigSpec SPEC;

    // market
    private static final ModConfigSpec.ConfigValue<String> FORMULA;
    private static final ModConfigSpec.DoubleValue CEILING;
    private static final ModConfigSpec.DoubleValue MIN_UNIT;
    private static final ModConfigSpec.LongValue REFERENCE_STOCK;
    private static final ModConfigSpec.EnumValue<InitialStock> INITIAL_STOCK;
    private static final ModConfigSpec.DoubleValue RANDOM_MIN;
    private static final ModConfigSpec.DoubleValue RANDOM_MAX;
    private static final ModConfigSpec.BooleanValue DYNAMIC_PROPAGATION;
    private static final ModConfigSpec.DoubleValue LABOR;
    private static final ModConfigSpec.BooleanValue DAMAGED_ITEMS;
    // currency
    private static final ModConfigSpec.ConfigValue<List<? extends String>> CURRENCY;
    private static final ModConfigSpec.BooleanValue LIGHTMANS_COINS;
    private static final ModConfigSpec.BooleanValue LIGHTMANS_BANK;
    private static final ModConfigSpec.DoubleValue LIGHTMANS_RATE;
    // fees
    private static final ModConfigSpec.DoubleValue TAX_SELL;
    private static final ModConfigSpec.DoubleValue TAX_BUY;
    private static final ModConfigSpec.BooleanValue BACKOFF_ENABLED;
    private static final ModConfigSpec.IntValue BACKOFF_THRESHOLD;
    private static final ModConfigSpec.IntValue BACKOFF_WINDOW;
    private static final ModConfigSpec.DoubleValue BACKOFF_PERCENT;
    private static final ModConfigSpec.IntValue BACKOFF_MAX;
    private static final ModConfigSpec.LongValue TREASURY_INITIAL;
    // machines
    private static final ModConfigSpec.IntValue MACHINE_INTERVAL;
    private static final ModConfigSpec.IntValue MACHINE_BATCH;
    private static final ModConfigSpec.IntValue VILLAGER_MAX_STACKS;
    // database
    private static final ModConfigSpec.EnumValue<DbType> DB_TYPE;
    private static final ModConfigSpec.ConfigValue<String> SQLITE_FILE;
    private static final ModConfigSpec.ConfigValue<String> PG_URL;
    private static final ModConfigSpec.ConfigValue<String> PG_USER;
    private static final ModConfigSpec.ConfigValue<String> PG_PASSWORD;
    private static final ModConfigSpec.ConfigValue<String> SERVER_ID;
    private static final ModConfigSpec.IntValue CACHE_REFRESH;
    private static final ModConfigSpec.IntValue HISTORY_DAYS;
    // triggers
    private static final ModConfigSpec.IntValue TRIGGER_INTERVAL;

    public enum InitialStock { REFERENCE, RANDOM, ZERO }

    public enum DbType { SQLITE, POSTGRES }

    // baked values
    public static PricingEngine.Params pricing = new PricingEngine.Params(PriceExpression.parse("b * (r / x)^1"), -1, 0.001);
    public static long referenceStock = 1000;
    public static InitialStock initialStock = InitialStock.REFERENCE;
    public static double randomMin = 0.5, randomMax = 1.5;
    public static boolean dynamicPropagation = true;
    public static double laborPercent = 0;
    public static boolean damagedItems = true;
    /** Money items -> value in milli-U.M. */
    public static Map<String, Long> currency = new LinkedHashMap<>(Map.of("minecraft:emerald", 1000L, "minecraft:emerald_block", 9000L));
    public static boolean lightmansCoins = true, lightmansBank = true;
    public static double lightmansRate = 0.01;
    public static double taxSellPercent = 0, taxBuyPercent = 0;
    public static BackoffTracker.Settings backoff = new BackoffTracker.Settings(true, 3, 600_000, 10, 20);
    public static long treasuryInitial = -1;
    public static int machineIntervalTicks = 20, machineBatch = 64, villagerMaxStacks = 27;
    public static DbType dbType = DbType.SQLITE;
    public static String sqliteFile = "freetrade.db", pgUrl = "", pgUser = "", pgPassword = "", serverId = "default";
    public static int cacheRefreshSeconds = 10, historyDays = 90, triggerIntervalTicks = 100;

    static {
        ModConfigSpec.Builder b = new ModConfigSpec.Builder();

        b.push("market");
        FORMULA = b.comment(
                        "Price of ONE unit as a function of the association's stock. Variables:",
                        "  x = units in stock, b = base value of the item (U.M.), r = reference stock of the item.",
                        "With the default, an item is worth b when x = r; half the stock doubles the price (power law).",
                        "Operators: + - * / ^  Functions: min max pow log ln sqrt abs exp. Example: b * (r / x)^2")
                .define("formula", "b * (r / x)^1", FreeTradeConfig::validFormula);
        CEILING = b.comment("Maximum price of one unit in U.M. -1 = no ceiling.")
                .defineInRange("price_ceiling", -1.0, -1.0, 1e15);
        MIN_UNIT = b.comment("Minimum price of one unit in U.M. (keeps abundant items from being worth 0).")
                .defineInRange("min_unit_price", 0.001, 0.0, 1e9);
        REFERENCE_STOCK = b.comment("Default reference stock r (per-item overrides in values.json).")
                .defineInRange("reference_stock", 1000L, 1L, Long.MAX_VALUE / 4);
        INITIAL_STOCK = b.comment(
                        "Stock of an item the first time the association sees it:",
                        "REFERENCE = r (price starts at the base value), RANDOM = random between",
                        "random_min*r and random_max*r, ZERO = empty (players must sell first).",
                        "Per-item initial stock in values.json always wins.")
                .defineEnum("initial_stock", InitialStock.REFERENCE);
        RANDOM_MIN = b.defineInRange("random_min", 0.5, 0.0, 100.0);
        RANDOM_MAX = b.defineInRange("random_max", 1.5, 0.0, 100.0);
        DYNAMIC_PROPAGATION = b.comment(
                        "Items without a declared value take their base value from the CURRENT price of their",
                        "ingredients, so selling lots of logs also makes planks, sticks, chests... cheaper.")
                .define("dynamic_propagation", true);
        LABOR = b.comment("Percent added to the value of a crafted item on top of its ingredients (per crafting step).")
                .defineInRange("labor_percent", 0.0, 0.0, 1000.0);
        DAMAGED_ITEMS = b.comment("Accept damaged tools/armor, paying proportionally to the remaining durability.")
                .define("accept_damaged_items", true);
        b.pop();

        b.push("currency");
        CURRENCY = b.comment(
                        "Items that ARE money, with how many U.M. one of them is worth (\"item_id=value\", decimals allowed).",
                        "Money items have a fixed value and are never traded as goods. Change is given in the",
                        "largest denominations first.")
                .defineListAllowEmpty("currency_items", List.of("minecraft:emerald=1", "minecraft:emerald_block=9"),
                        () -> "minecraft:emerald=1", FreeTradeConfig::validCurrency);
        LIGHTMANS_COINS = b.comment("If Lightman's Currency is installed, its coins are accepted as money.")
                .define("lightmans_coins", true);
        LIGHTMANS_BANK = b.comment("If Lightman's Currency is installed, players can pay/receive through their bank account.")
                .define("lightmans_bank", true);
        LIGHTMANS_RATE = b.comment("How many U.M. one unit of Lightman's Currency core value is worth",
                        "(the copper coin is 1 core value; default: 1 copper coin = 0.01 U.M., 1 gold coin = 1 U.M.).")
                .defineInRange("lightmans_um_per_core_value", 0.01, 0.001, 1e9);
        b.pop();

        b.push("fees");
        TAX_SELL = b.comment("Percent of every sale to the association that goes to the shared OP account (tax).")
                .defineInRange("tax_on_sell_percent", 0.0, 0.0, 100.0);
        TAX_BUY = b.comment("Percent added to every purchase from the association that goes to the OP account.")
                .defineInRange("tax_on_buy_percent", 0.0, 0.0, 1000.0);
        BACKOFF_ENABLED = b.comment(
                        "Devaluation backoff: discourages using the association as storage. When a player buys back an",
                        "item they sold within backoff_window_seconds, a round trip is counted. From",
                        "backoff_threshold round trips on, every round trip raises the buy price of that item for that",
                        "player by backoff_percent (stacking up to backoff_max_level). One level decays per idle window.")
                .define("backoff_enabled", true);
        BACKOFF_THRESHOLD = b.defineInRange("backoff_threshold", 3, 1, 1000);
        BACKOFF_WINDOW = b.defineInRange("backoff_window_seconds", 600, 1, 86400 * 30);
        BACKOFF_PERCENT = b.defineInRange("backoff_percent", 10.0, 0.0, 1000.0);
        BACKOFF_MAX = b.defineInRange("backoff_max_level", 20, 1, 1000);
        TREASURY_INITIAL = b.comment(
                        "Money the association starts with (U.M.). -1 = infinite: it always pays.",
                        "When finite, sales refill it and purchases drain it; without money it stops buying.")
                .defineInRange("treasury_initial", -1L, -1L, Long.MAX_VALUE / 1000);
        b.pop();

        b.push("machines");
        MACHINE_INTERVAL = b.comment("Ticks between automatic sales/purchases of the Sell Point and Auto Buyer.")
                .defineInRange("interval_ticks", 20, 1, 72000);
        MACHINE_BATCH = b.comment("Most units a machine trades per operation.")
                .defineInRange("batch_size", 64, 1, 100000);
        VILLAGER_MAX_STACKS = b.comment("Stacks of one item a player can put in the trade representative's sell slot.")
                .defineInRange("villager_max_stacks", 27, 1, 1000);
        b.pop();

        b.push("database");
        DB_TYPE = b.comment(
                        "SQLITE: a file inside the world folder (single player / one server).",
                        "POSTGRES: recommended for servers; several servers may share one database = one economy",
                        "(all of them must run the same modpack).")
                .defineEnum("type", DbType.SQLITE);
        SQLITE_FILE = b.comment("SQLite file, relative to the world folder (or absolute).")
                .define("sqlite_file", "freetrade.db");
        PG_URL = b.comment("JDBC url, e.g. jdbc:postgresql://localhost:5432/freetrade")
                .define("postgres_url", "jdbc:postgresql://localhost:5432/freetrade");
        PG_USER = b.define("postgres_user", "freetrade");
        PG_PASSWORD = b.define("postgres_password", "");
        SERVER_ID = b.comment("Name of this server in the shared history (useful when several servers share Postgres).")
                .define("server_id", "default");
        CACHE_REFRESH = b.comment("Seconds between reloads of stock from the database (only matters when shared).")
                .defineInRange("cache_refresh_seconds", 10, 1, 3600);
        HISTORY_DAYS = b.comment("Days of transaction history kept. 0 = forever.")
                .defineInRange("history_days", 90, 0, 36500);
        b.pop();

        b.push("triggers");
        TRIGGER_INTERVAL = b.comment("Ticks between periodic checks of treasury/tax account triggers.")
                .defineInRange("check_interval_ticks", 100, 1, 72000);
        b.pop();

        SPEC = b.build();
    }

    private FreeTradeConfig() {}

    private static boolean validFormula(Object o) {
        if (!(o instanceof String s)) return false;
        try {
            PriceExpression.parse(s);
            return true;
        } catch (IllegalArgumentException e) {
            FreeTrade.LOG.error(e.getMessage());
            return false;
        }
    }

    private static boolean validCurrency(Object o) {
        if (!(o instanceof String s)) return false;
        int i = s.lastIndexOf('=');
        if (i <= 0) return false;
        try {
            return Double.parseDouble(s.substring(i + 1).trim()) >= 0.001;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    public static void bake() {
        pricing = new PricingEngine.Params(PriceExpression.parse(FORMULA.get()), CEILING.get(), MIN_UNIT.get());
        referenceStock = REFERENCE_STOCK.get();
        initialStock = INITIAL_STOCK.get();
        randomMin = RANDOM_MIN.get();
        randomMax = Math.max(RANDOM_MIN.get(), RANDOM_MAX.get());
        dynamicPropagation = DYNAMIC_PROPAGATION.get();
        laborPercent = LABOR.get();
        damagedItems = DAMAGED_ITEMS.get();
        Map<String, Long> cur = new LinkedHashMap<>();
        for (String s : new ArrayList<>(CURRENCY.get())) {
            int i = s.lastIndexOf('=');
            cur.put(s.substring(0, i).trim(), Money.ofDouble(Double.parseDouble(s.substring(i + 1).trim())));
        }
        currency = cur;
        lightmansCoins = LIGHTMANS_COINS.get();
        lightmansBank = LIGHTMANS_BANK.get();
        lightmansRate = LIGHTMANS_RATE.get();
        taxSellPercent = TAX_SELL.get();
        taxBuyPercent = TAX_BUY.get();
        backoff = new BackoffTracker.Settings(BACKOFF_ENABLED.get(), BACKOFF_THRESHOLD.get(),
                BACKOFF_WINDOW.get() * 1000L, BACKOFF_PERCENT.get(), BACKOFF_MAX.get());
        treasuryInitial = TREASURY_INITIAL.get();
        machineIntervalTicks = MACHINE_INTERVAL.get();
        machineBatch = MACHINE_BATCH.get();
        villagerMaxStacks = VILLAGER_MAX_STACKS.get();
        dbType = DB_TYPE.get();
        sqliteFile = SQLITE_FILE.get();
        pgUrl = PG_URL.get();
        pgUser = PG_USER.get();
        pgPassword = PG_PASSWORD.get();
        serverId = SERVER_ID.get();
        cacheRefreshSeconds = CACHE_REFRESH.get();
        historyDays = HISTORY_DAYS.get();
        triggerIntervalTicks = TRIGGER_INTERVAL.get();
    }
}
