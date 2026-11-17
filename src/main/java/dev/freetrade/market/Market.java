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
        return !s.currency().contains(item) && (s.declared().containsKey(item) || s.derived().containsKey(item));
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
        Double m = unitMemo.get(item);
        if (m != null) return m;
        double b = baseValue(item);
        double p = b <= 0 ? 0 : engine.unitValue(b, reference(item), Math.max(1, stock(item)));
        if (visiting.get().isEmpty()) unitMemo.put(item, p);
        return p;
    }

    /** Milli-U.M. the association pays now for n units (before tax), scaled by durability factor. */
    public long quoteSell(String item, long n, double factor) {
        if (!isTradeable(item)) return 0;
        return (long) (engine.sellValue(baseValue(item), reference(item), stock(item), n) * factor);
    }

    public long quoteSellNet(String item, long n, double factor) {
        long gross = quoteSell(item, n, factor);
        return gross - taxOf(gross, settings.taxSellPercent());
    }

    /** Milli-U.M. the player pays now for n units, including tax and the player's current backoff. -1 if not possible. */
    public long quoteBuy(String item, long n, double backoffMultiplier) {
        if (!isTradeable(item)) return -1;
        long cost = PricingEngine.applyMultiplier(engine.buyCost(baseValue(item), reference(item), stock(item), n),
                backoffMultiplier);
        if (cost < 0) return -1;
        return cost + taxOf(cost, settings.taxBuyPercent());
    }

    private static long taxOf(long amount, double percent) {
        if (percent <= 0 || amount <= 0) return 0;
        return (long) Math.floor(amount * percent / 100.0);
    }

    // ------------------------------------------------------------------ trades (database thread)

    /**
     * Sells up to {@code offered} units. With {@code wholeUnits} only the units needed for a whole number of
     * U.M. are taken and the payout is whole U.M.; otherwise the exact milli payout is returned.
     */
    public CompletableFuture<SellResult> sell(TradeContext ctx, String item, long offered, double factor, boolean wholeUnits) {
        if (!isTradeable(item) || offered <= 0) {
            return CompletableFuture.completedFuture(new SellResult(Error.NOT_TRADEABLE, 0, 0, 0, stock(item)));
        }
        final Settings st = settings;
        return db.submit(c -> {
            long now = System.currentTimeMillis();
            long s = store.lockStock(c, item, stock(item));
            double b = baseValue(item), r = reference(item);
            double netFactor = factor * (1 - st.taxSellPercent() / 100.0);
            long units = wholeUnits ? engine.unitsForWholePayout(b, r, s, offered, netFactor) : offered;
            if (units <= 0) return new SellResult(Error.TOO_FEW, 0, 0, 0, s);
            long treasury = Long.MAX_VALUE;
            if (st.treasuryInitialUm() >= 0) {
                treasury = store.lockAccount(c, MarketStore.TREASURY);
                if (grossOf(b, r, s, units, factor) > treasury) {
                    long lo = 0, hi = units;
                    while (lo < hi) {
                        long mid = (lo + hi + 1) >>> 1;
                        if (grossOf(b, r, s, mid, factor) <= treasury) lo = mid;
                        else hi = mid - 1;
                    }
                    units = lo;
                    if (wholeUnits && units > 0) units = engine.unitsForWholePayout(b, r, s, units, netFactor);
                    if (units <= 0) return new SellResult(Error.TREASURY_EMPTY, 0, 0, 0, s);
                }
            }
            long gross = grossOf(b, r, s, units, factor);
            long tax = taxOf(gross, st.taxSellPercent());
            long net = gross - tax;
            if (wholeUnits) net = Money.ofUnits(Money.floorUnits(net));
            long after = store.addStock(c, item, units);
            if (st.treasuryInitialUm() >= 0) store.addAccount(c, MarketStore.TREASURY, -(net + tax));
            if (tax > 0) store.addAccount(c, MarketStore.TAX, tax);
            if (st.backoff().enabled() && ctx.player() != null) {
                BackoffTracker.State bs = store.lockBackoff(c, ctx.player(), item);
                BackoffTracker.onSell(bs, now);
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

    private long grossOf(double b, double r, long s, long units, double factor) {
        return (long) (engine.sellValue(b, r, s, units) * factor);
    }

    /**
     * Buys from the association. {@code exact}: exactly {@code units} or nothing, failing if the cost exceeds the
     * budget. Otherwise buys as many as the budget allows, up to {@code units}.
     */
    public CompletableFuture<BuyResult> buy(TradeContext ctx, String item, long units, long budgetMilli, boolean exact) {
        if (!isTradeable(item) || units <= 0) {
            return CompletableFuture.completedFuture(new BuyResult(Error.NOT_TRADEABLE, 0, 0, 0, stock(item)));
        }
        final Settings st = settings;
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
            double taxMult = 1 + st.taxBuyPercent() / 100.0;
            long n;
            if (exact) {
                if (units > s) return new BuyResult(Error.NO_STOCK, 0, 0, 0, s);
                n = units;
            } else {
                n = Math.min(units, engine.maxAffordable(b, r, s, budgetMilli, mult * taxMult));
                if (n <= 0) return new BuyResult(Error.NO_FUNDS, 0, 0, 0, s);
            }
            long cost = PricingEngine.applyMultiplier(engine.buyCost(b, r, s, n), mult);
            long tax = taxOf(cost, st.taxBuyPercent());
            if (cost + tax > budgetMilli) {
                if (!exact) {
                    // rounding edge: drop one unit
                    n--;
                    if (n <= 0) return new BuyResult(Error.NO_FUNDS, 0, 0, 0, s);
                    cost = PricingEngine.applyMultiplier(engine.buyCost(b, r, s, n), mult);
                    tax = taxOf(cost, st.taxBuyPercent());
                }
                if (cost + tax > budgetMilli) return new BuyResult(Error.NO_FUNDS, 0, cost + tax, 0, s);
            }
            long after = store.addStock(c, item, -n);
            if (st.treasuryInitialUm() >= 0) store.addAccount(c, MarketStore.TREASURY, cost);
            if (tax > 0) store.addAccount(c, MarketStore.TAX, tax);
            if (bs != null) {
                BackoffTracker.onBuy(bs, st.backoff(), now);
                store.saveBackoff(c, ctx.player(), item, bs);
            }
            store.insertTx(c, new MarketStore.Tx(now, st.serverId(), ctx.player(), ctx.playerName(), ctx.source(), item,
                    -n, cost + tax, tax, after));
            accounts.putAll(store.loadAccounts(c));
            return new BuyResult(Error.NONE, n, cost + tax, tax, after);
        }).thenApply(res -> {
            if (res.ok()) {
                stock.put(item, res.stockAfter());
                changed();
            }
            return res;
        }).exceptionally(t -> new BuyResult(Error.DATABASE, 0, 0, 0, stock(item)));
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
