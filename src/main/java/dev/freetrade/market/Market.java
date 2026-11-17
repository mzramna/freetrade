package dev.freetrade.market;

import java.sql.SQLException;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

import dev.freetrade.db.Database;
import dev.freetrade.db.MarketStore;
import dev.freetrade.valuation.ValueSolver;

/**
 * The Trade Association: prices, the shared stock and the money accounts. Item ids are plain strings so
 * this class does not depend on Minecraft. Reads come from in-memory caches (safe on the game thread);
 * trades run as database transactions on the database thread and refresh the caches when committed.
 */
public final class Market {
    /** Everything valuation produced. */
    public record Snapshot(Map<String, Double> declared, Map<String, Double> derived,
                           Map<String, ValueSolver.Derivation> derivations, Map<String, Long> reference,
                           Map<String, Long> initialStock, Set<String> currency) {
        public static Snapshot empty() {
            return new Snapshot(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Set.of());
        }
    }

    public record Settings(long defaultReference, boolean dynamicPropagation, double laborPercent, double taxSellPercent,
                           double taxBuyPercent, long treasuryInitialUm, BackoffTracker.Settings backoff, String serverId) {}

    public record TradeContext(String player, String playerName, String source) {}

    public enum Error { NONE, NOT_TRADEABLE, TOO_FEW, NO_STOCK, NO_FUNDS, TREASURY_EMPTY, DATABASE }

    public record SellResult(Error error, long units, long payoutMilli, long taxMilli, long stockAfter) {
        public boolean ok() {
            return error == Error.NONE;
        }
    }

    public record BuyResult(Error error, long units, long costMilli, long taxMilli, long stockAfter) {
        public boolean ok() {
            return error == Error.NONE;
        }
    }

    private final Database db;
    private final MarketStore store;
    private final PricingEngine engine;
    private volatile Settings settings;
    private volatile Snapshot snapshot = Snapshot.empty();
    private final Map<String, Long> stock = new ConcurrentHashMap<>();
    private final Map<String, Long> accounts = new ConcurrentHashMap<>();
    private final Map<String, Double> unitMemo = new ConcurrentHashMap<>();
    /** Per-item taxes from values.json: they replace the global tax percentage of that direction. */
    private volatile Map<String, TaxRule> sellTaxes = Map.of(), buyTaxes = Map.of();
    /** Admin-forced unit prices (U.M.): they replace the formula for that item. Shared through the database. */
    private final Map<String, Double> overrides = new ConcurrentHashMap<>();
    private final ThreadLocal<Set<String>> visiting = ThreadLocal.withInitial(HashSet::new);
    private final CopyOnWriteArrayList<Consumer<Market>> listeners = new CopyOnWriteArrayList<>();

    public Market(Database db, PricingEngine.Params pricing, Settings settings) throws SQLException {
        this.db = db;
        this.store = new MarketStore(db.dialect());
        this.engine = new PricingEngine(pricing);
        this.settings = settings;
        db.call(c -> {
            store.migrate(c, settings.treasuryInitialUm() < 0 ? 0 : Money.ofUnits(settings.treasuryInitialUm()));
            return null;
        });
        reloadCaches();
    }

    public Database database() {
        return db;
    }

    public MarketStore store() {
        return store;
    }

    public PricingEngine engine() {
        return engine;
    }

    public Settings settings() {
        return settings;
    }

    public void reconfigure(PricingEngine.Params pricing, Settings s) {
        engine.setParams(pricing);
        this.settings = s;
        unitMemo.clear();
    }

    /** Called after every committed change of stock or accounts (on the database thread). */
    public void addListener(Consumer<Market> l) {
        listeners.add(l);
    }

    private void changed() {
        unitMemo.clear();
        for (Consumer<Market> l : listeners) {
            try {
                l.accept(this);
            } catch (RuntimeException ignored) {
            }
        }
    }

    public void reloadCaches() throws SQLException {
        Map<String, Long> s = db.call(store::loadStock);
        Map<String, Long> a = db.call(store::loadAccounts);
        Map<String, Double> o = db.call(store::loadOverrides);
        overrides.clear();
        overrides.putAll(o);
        stock.clear();
        stock.putAll(s);
        accounts.clear();
        accounts.putAll(a);
        unitMemo.clear();
    }

    /** Async refresh used when several servers share the database. */
    public CompletableFuture<Void> refreshAsync() {
        return db.submit(c -> {
            Map<String, Long> s = store.loadStock(c);
            Map<String, Long> a = store.loadAccounts(c);
            Map<String, Double> o = store.loadOverrides(c);
            overrides.keySet().retainAll(o.keySet());
            overrides.putAll(o);
            stock.putAll(s);
            accounts.putAll(a);
            changed();
            return null;
        });
    }

    /** Installs a new valuation and creates stock rows for every priced item that has none. */
    public void install(Snapshot snap, Map<String, Long> initialStocks) throws SQLException {
        this.snapshot = snap;
        db.call(c -> {
            store.ensureStock(c, initialStocks);
            return null;
        });
        reloadCaches();
    }

    public Snapshot snapshot() {
        return snapshot;
    }

    // ------------------------------------------------------------------ reads (cache)

    public boolean isCurrency(String item) {
        return snapshot.currency().contains(item);
    }

    public boolean isTradeable(String item) {
        Snapshot s = snapshot;
        return !s.currency().contains(item)
                && (s.declared().containsKey(item) || s.derived().containsKey(item) || overrides.containsKey(item));
    }

    public long stock(String item) {
        Long v = stock.get(item);
        if (v != null) return v;
        Long init = snapshot.initialStock().get(item);
        return init == null ? 0 : init;
    }

    public Map<String, Long> stockView() {
        return java.util.Collections.unmodifiableMap(stock);
    }

    public long account(String name) {
        return accounts.getOrDefault(name, 0L);
    }

    /** Updates the cached balance after a change made directly through the store. */
    public void cacheAccount(String name, long balance) {
        accounts.put(name, balance);
        unitMemo.clear();
    }

    public boolean treasuryInfinite() {
        return settings.treasuryInitialUm() < 0;
    }

    public long reference(String item) {
        Long r = snapshot.reference().get(item);
        return r != null ? r : settings.defaultReference();
    }

    /** Base value b in U.M.: declared, or derived from the current prices of the ingredients. */
    public double baseValue(String item) {
        Snapshot s = snapshot;
        Double declared = s.declared().get(item);
        if (declared != null) return declared;
        Double derived = s.derived().get(item);
        if (derived == null) return 0;
        ValueSolver.Derivation d = s.derivations().get(item);
        if (!settings.dynamicPropagation() || d == null) return derived;
        Set<String> v = visiting.get();
        if (!v.add(item)) return derived;
        try {
            double dyn = ValueSolver.dynamicBase(d, this::unitPrice, settings.laborPercent());
            return dyn > 0 ? dyn : derived;
        } finally {
            v.remove(item);
        }
    }

    /** Current market value of one unit (at the current stock level). */
    public double unitPrice(String item) {
        Double forced = overrides.get(item);
        if (forced != null) return forced;
        Double m = unitMemo.get(item);
        if (m != null) return m;
        double b = baseValue(item);
        double p = b <= 0 ? 0 : engine.unitValue(b, reference(item), Math.max(1, stock(item)));
        if (visiting.get().isEmpty()) unitMemo.put(item, p);
        return p;
    }

    /**
     * Units per lot: items worth less than 1 U.M. are only traded in multiples of a lot worth about 1 U.M.
     * (a stick worth 0.5 U.M. is traded 2 at a time; never a single one). Items worth 1 U.M. or more: 1.
     */
    public long lotSize(String item) {
        double unit = unitPrice(item);
        if (unit <= 0) return 1;
        if (unit >= 1.0) return 1;
        // nearest whole count, at least 2: a lot is then worth about 1 U.M. and small price moves
        // (0.5 -> 0.499) do not change the lot size
        return Math.min(1_000_000L, Math.max(2L, Math.round(1.0 / unit)));
    }

    private static long floorToLot(long n, long lot) {
        return lot <= 1 ? n : (n / lot) * lot;
    }

    private static long ceilToLot(long n, long lot) {
        return lot <= 1 ? n : ((n + lot - 1) / lot) * lot;
    }

    // ------------------------------------------------------------------ forced prices

    /** Forced unit price of an item (U.M.), or null when the formula applies. */
    public Double forcedPrice(String item) {
        return overrides.get(item);
    }

    public Map<String, Double> forcedPrices() {
        return java.util.Collections.unmodifiableMap(overrides);
    }

    /** Sets (price > 0) or clears (price <= 0) a forced unit price. */
    public CompletableFuture<Double> forcePrice(String item, double price) {
        return db.submit(c -> {
            if (price > 0) store.setOverride(c, item, price);
            else store.deleteOverride(c, item);
            return price;
        }).thenApply(v -> {
            if (v > 0) overrides.put(item, v);
            else overrides.remove(item);
            changed();
            return v;
        });
    }

    /** Milli the association pays for n units starting at stock s (formula, or the forced price). */
    private long sellSum(String item, double b, double r, long s, long n) {
        Double forced = overrides.get(item);
        if (forced != null) return (long) Math.floor(forced * n * Money.SCALE);
        return engine.sellValue(b, r, s, n);
    }

    /** Milli charged for n units taken from stock s (formula, or the forced price); -1 if not enough stock. */
    private long buySum(String item, double b, double r, long s, long n) {
        if (n > s) return -1;
        Double forced = overrides.get(item);
        if (forced != null) return (long) Math.ceil(forced * n * Money.SCALE);
        return engine.buyCost(b, r, s, n);
    }

    /** Real (unrounded) milli the association would pay for n units, after tax and durability, before backoff. */
    private long realSellNet(String item, long n, double factor) {
        long gross = (long) (sellSum(item, baseValue(item), reference(item), stock(item), n) * factor);
        return gross - sellTax(item, gross, n, settings.taxSellPercent());
    }

    /**
     * Whole U.M. (as milli) the association pays now for n units (rounded down to whole lots): the real total is
     * computed first and then rounded half-up.
     */
    public long quoteSell(String item, long n, double factor) {
        if (!isTradeable(item)) return 0;
        long units = floorToLot(n, lotSize(item));
        if (units <= 0) return 0;
        return Money.roundHalfUp(realSellNet(item, units, factor));
    }

    /**
     * Whole U.M. (as milli) the player pays now for n units (rounded up to whole lots), including tax and backoff:
     * the real total is computed first and then rounded half-up. -1 if there is not enough stock.
     */
    public long quoteBuy(String item, long n, double backoffMultiplier) {
        if (!isTradeable(item)) return -1;
        long units = ceilToLot(n, lotSize(item));
        long cost = PricingEngine.applyMultiplier(buySum(item, baseValue(item), reference(item), stock(item), units),
                backoffMultiplier);
        if (cost < 0) return -1;
        return Money.roundHalfUp(cost + buyTax(item, cost, units, settings.taxBuyPercent()));
    }

    /** Units actually traded for a request of n (sell: down to whole lots, buy: up to whole lots). */
    public long sellUnits(String item, long n) {
        return floorToLot(n, lotSize(item));
    }

    public long buyUnits(String item, long n) {
        return ceilToLot(n, lotSize(item));
    }

    /** Current backoff multiplier of a player for an item (read from the database). */
    public CompletableFuture<Double> backoffMultiplier(String player, String item) {
        final Settings st = settings;
        if (!st.backoff().enabled() || player == null) return CompletableFuture.completedFuture(1.0);
        return db.submit(c -> BackoffTracker.peek(store.lockBackoff(c, player, item), st.backoff(),
                System.currentTimeMillis())).exceptionally(t -> 1.0);
    }

    public void setItemTaxes(Map<String, TaxRule> sell, Map<String, TaxRule> buy) {
        this.sellTaxes = Map.copyOf(sell);
        this.buyTaxes = Map.copyOf(buy);
        unitMemo.clear();
    }

    public TaxRule sellTaxRule(String item) {
        return sellTaxes.get(item);
    }

    public TaxRule buyTaxRule(String item) {
        return buyTaxes.get(item);
    }

    private long sellTax(String item, long gross, long units, double globalPercent) {
        TaxRule rule = sellTaxes.get(item);
        long tax = rule != null ? rule.apply(gross, units) : taxOf(gross, globalPercent);
        return Math.min(Math.max(0, gross), tax); // a sale never costs the player money
    }

    private long buyTax(String item, long cost, long units, double globalPercent) {
        TaxRule rule = buyTaxes.get(item);
        return rule != null ? rule.apply(cost, units) : taxOf(cost, globalPercent);
    }

    private static long taxOf(long amount, double percent) {
        if (percent <= 0 || amount <= 0) return 0;
        return (long) Math.floor(amount * percent / 100.0);
    }

    // ------------------------------------------------------------------ trades (database thread)

    /**
     * Sells up to {@code offered} units, in whole lots. The real value of all units is computed (slippage, tax,
     * durability, this player's backoff) and then rounded half-up to whole U.M. Leftover units are not taken.
     */
    public CompletableFuture<SellResult> sell(TradeContext ctx, String item, long offered, double factor) {
        if (!isTradeable(item) || offered <= 0) {
            return CompletableFuture.completedFuture(new SellResult(Error.NOT_TRADEABLE, 0, 0, 0, stock(item)));
        }
        final Settings st = settings;
        final long lot = lotSize(item);
        return db.submit(c -> {
            long now = System.currentTimeMillis();
            long s = store.lockStock(c, item, stock(item));
            double b = baseValue(item), r = reference(item);
            BackoffTracker.State bs = null;
            double penalty = 1.0;
            if (st.backoff().enabled() && ctx.player() != null) {
                bs = store.lockBackoff(c, ctx.player(), item);
                penalty = BackoffTracker.current(bs, st.backoff(), now);
            }
            long units = floorToLot(offered, lot);
            if (units <= 0) return new SellResult(Error.TOO_FEW, 0, 0, 0, s);
            long[] parts = sellParts(item, b, r, s, units, factor, penalty, st.taxSellPercent());
            if (st.treasuryInitialUm() >= 0) {
                long treasury = store.lockAccount(c, MarketStore.TREASURY);
                while (units > 0 && parts[0] + parts[1] > treasury) {
                    units -= lot;
                    parts = units > 0 ? sellParts(item, b, r, s, units, factor, penalty, st.taxSellPercent()) : parts;
                }
                if (units <= 0) return new SellResult(Error.TREASURY_EMPTY, 0, 0, 0, s);
            }
            long net = parts[0], tax = parts[1], netReal = parts[2];
            if (net <= 0) return new SellResult(Error.TOO_FEW, 0, 0, 0, s);
            long after = store.addStock(c, item, units);
            if (st.treasuryInitialUm() >= 0) store.addAccount(c, MarketStore.TREASURY, -(net + tax));
            if (tax > 0) store.addAccount(c, MarketStore.TAX, tax);
            if (bs != null) {
                BackoffTracker.onSell(bs, now);
                if (net > netReal) BackoffTracker.onRoundingGain(bs, st.backoff(), now);
                store.saveBackoff(c, ctx.player(), item, bs);
            }
            store.insertTx(c, new MarketStore.Tx(now, st.serverId(), ctx.player(), ctx.playerName(), ctx.source(), item,
                    units, net, tax, after));
            accounts.putAll(store.loadAccounts(c));
            return new SellResult(Error.NONE, units, net, tax, after);
        }).thenApply(res -> {
            if (res.ok()) {
                stock.put(item, res.stockAfter());
                changed();
            }
            return res;
        }).exceptionally(t -> new SellResult(Error.DATABASE, 0, 0, 0, stock(item)));
    }

    /** {rounded net payout, tax, real net} in milli. */
    private long[] sellParts(String item, double b, double r, long s, long units, double factor, double penalty,
                             double taxPercent) {
        long gross = (long) (sellSum(item, b, r, s, units) * factor / penalty);
        long tax = sellTax(item, gross, units, taxPercent);
        long netReal = gross - tax;
        return new long[]{Money.roundHalfUp(netReal), tax, netReal};
    }

    /** {rounded total, tax, real total} in milli, or null when there is not enough stock. */
    private long[] buyParts(String item, double b, double r, long s, long units, double mult, double taxPercent) {
        long cost = PricingEngine.applyMultiplier(buySum(item, b, r, s, units), mult);
        if (cost < 0) return null;
        long tax = buyTax(item, cost, units, taxPercent);
        long real = cost + tax;
        return new long[]{Money.roundHalfUp(real), tax, real};
    }

    /**
     * Buys from the association, in whole lots. {@code exact}: exactly {@code units} (rounded up to whole lots) or
     * nothing, failing if the cost exceeds the budget. Otherwise as many whole lots as the budget allows, up to
     * {@code units}. The real total is computed first and then rounded half-up to whole U.M.
     */
    public CompletableFuture<BuyResult> buy(TradeContext ctx, String item, long units, long budgetMilli, boolean exact) {
        if (!isTradeable(item) || units <= 0) {
            return CompletableFuture.completedFuture(new BuyResult(Error.NOT_TRADEABLE, 0, 0, 0, stock(item)));
        }
        final Settings st = settings;
        final long lot = lotSize(item);
        return db.submit(c -> {
            long now = System.currentTimeMillis();
            long s = store.lockStock(c, item, stock(item));
            if (s <= 0) return new BuyResult(Error.NO_STOCK, 0, 0, 0, s);
            double b = baseValue(item), r = reference(item);
            BackoffTracker.State bs = null;
            double mult = 1.0;
            if (st.backoff().enabled() && ctx.player() != null) {
                bs = store.lockBackoff(c, ctx.player(), item);
                mult = BackoffTracker.peek(bs, st.backoff(), now);
            }
            long n;
            long[] parts;
            if (exact) {
                n = ceilToLot(units, lot);
                if (n > s) return new BuyResult(Error.NO_STOCK, 0, 0, 0, s);
                parts = buyParts(item, b, r, s, n, mult, st.taxBuyPercent());
                if (parts == null) return new BuyResult(Error.NO_STOCK, 0, 0, 0, s);
                if (parts[0] > budgetMilli) return new BuyResult(Error.NO_FUNDS, 0, parts[0], 0, s);
            } else {
                long max = floorToLot(Math.min(units, s), lot);
                // binary search on whole lots for the most the budget pays (rounded totals grow with n)
                long lo = 0, hi = max / Math.max(1, lot);
                while (lo < hi) {
                    long mid = (lo + hi + 1) >>> 1;
                    long[] p = buyParts(item, b, r, s, mid * lot, mult, st.taxBuyPercent());
                    if (p != null && p[0] <= budgetMilli) lo = mid;
                    else hi = mid - 1;
                }
                n = lo * lot;
                if (n <= 0) return new BuyResult(Error.NO_FUNDS, 0, 0, 0, s);
                parts = buyParts(item, b, r, s, n, mult, st.taxBuyPercent());
            }
            long total = parts[0], tax = parts[1], real = parts[2];
            long after = store.addStock(c, item, -n);
            if (st.treasuryInitialUm() >= 0) store.addAccount(c, MarketStore.TREASURY, total - tax);
            if (tax > 0) store.addAccount(c, MarketStore.TAX, tax);
            if (bs != null) {
                BackoffTracker.onBuy(bs, st.backoff(), now);
                if (total < real) BackoffTracker.onRoundingGain(bs, st.backoff(), now);
                store.saveBackoff(c, ctx.player(), item, bs);
            }
            store.insertTx(c, new MarketStore.Tx(now, st.serverId(), ctx.player(), ctx.playerName(), ctx.source(), item,
                    -n, total, tax, after));
            accounts.putAll(store.loadAccounts(c));
            return new BuyResult(Error.NONE, n, total, tax, after);
        }).thenApply(res -> {
            if (res.ok()) {
                stock.put(item, res.stockAfter());
                changed();
            }
            return res;
        }).exceptionally(t -> new BuyResult(Error.DATABASE, 0, 0, 0, stock(item)));
    }

    /** Admin: add (delta > 0) or remove (delta < 0) stock atomically; never below zero. Returns the new stock. */
    public CompletableFuture<Long> adjustStock(String item, long delta) {
        return db.submit(c -> {
            long cur = store.lockStock(c, item, stock(item));
            long next = Math.max(0, cur + delta);
            store.setStock(c, item, next);
            return next;
        }).thenApply(v -> {
            stock.put(item, v);
            changed();
            return v;
        });
    }

    /** Admin: set stock. */
    public CompletableFuture<Long> setStock(String item, long value) {
        return db.submit(c -> {
            store.setStock(c, item, value);
            return value;
        }).thenApply(v -> {
            stock.put(item, v);
            changed();
            return v;
        });
    }

    /**
     * Moves money in or out of an account. With {@code requireFunds}, refuses (returns null) when the balance
     * would go negative. Logs the movement in the history with item "$account:&lt;name&gt;".
     */
    public CompletableFuture<Long> adjustAccount(TradeContext ctx, String account, long deltaMilli, boolean requireFunds) {
        final Settings st = settings;
        return db.submit(c -> {
            long bal = store.lockAccount(c, account);
            if (requireFunds && bal + deltaMilli < 0) return null;
            long after = store.addAccount(c, account, deltaMilli);
            store.insertTx(c, new MarketStore.Tx(System.currentTimeMillis(), st.serverId(), ctx.player(),
                    ctx.playerName(), ctx.source(), "$account:" + account, 0, deltaMilli, 0, after));
            return after;
        }).thenApply(v -> {
            if (v != null) {
                accounts.put(account, v);
                changed();
            }
            return v;
        });
    }
}
