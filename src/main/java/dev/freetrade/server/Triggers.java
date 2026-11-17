package dev.freetrade.server;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import dev.freetrade.FreeTrade;
import dev.freetrade.db.MarketStore;
import dev.freetrade.market.Market;
import dev.freetrade.market.Money;
import net.minecraft.server.MinecraftServer;

/**
 * Account triggers: "when the treasury/tax account reaches X, run command Y (and optionally take Z from it)".
 * once = fires a single time; repeat = fires again each time the condition becomes true (edge), or, with a
 * cooldown, at most once per cooldown while it stays true. Firing is decided inside a database transaction
 * that locks the trigger row, so servers sharing a database never fire the same trigger twice.
 */
public final class Triggers {
    private static final List<MarketStore.Trigger> cache = new CopyOnWriteArrayList<>();
    private static volatile boolean pending;

    private Triggers() {}

    static void clear() {
        cache.clear();
    }

    public static List<MarketStore.Trigger> list() {
        return List.copyOf(cache);
    }

    static void load(Market m) {
        try {
            List<MarketStore.Trigger> l = m.database().call(m.store()::loadTriggers);
            cache.clear();
            cache.addAll(l);
        } catch (Exception e) {
            FreeTrade.LOG.error("Could not load triggers", e);
        }
    }

    static void reloadAsync(Market m) {
        m.database().submit(m.store()::loadTriggers).thenAccept(l -> {
            cache.clear();
            cache.addAll(l);
        });
    }

    public static CompletableFuture<Void> save(Market m, MarketStore.Trigger t) {
        return m.database().submit(c -> {
            m.store().upsertTrigger(c, t);
            return m.store().loadTriggers(c);
        }).thenAccept(l -> {
            cache.clear();
            cache.addAll(l);
        });
    }

    public static CompletableFuture<Boolean> remove(Market m, String id) {
        return m.database().submit(c -> m.store().deleteTrigger(c, id)).thenApply(ok -> {
            cache.removeIf(t -> t.id().equals(id));
            return ok;
        });
    }

    /** Coalesces checks requested by many trades into one per tick. */
    static void checkSoon() {
        if (pending) return;
        pending = true;
        MinecraftServer s = FreeTradeServer.server();
        Market m = FreeTradeServer.market();
        if (s == null || m == null) {
            pending = false;
            return;
        }
        s.execute(() -> {
            pending = false;
            check(s, m);
        });
    }

    static void check(MinecraftServer server, Market m) {
        long now = System.currentTimeMillis();
        for (MarketStore.Trigger t : cache) {
            if (!t.enabled()) continue;
            boolean cond = t.test(m.account(t.account()));
            boolean wants;
            if ("once".equals(t.mode())) wants = cond && t.fired() == 0;
            else if (t.cooldownMs() > 0) wants = cond && now - t.lastFired() >= t.cooldownMs();
            else wants = cond && t.armed();
            if (wants) fire(server, m, t.id());
            else if (!cond && !t.armed() && "repeat".equals(t.mode())) rearm(m, t.id());
        }
    }

    private static void rearm(Market m, String id) {
        m.database().submit(c -> {
            MarketStore.Trigger t = m.store().lockTrigger(c, id);
            if (t == null) return null;
            long bal = m.store().lockAccount(c, t.account());
            if (t.test(bal)) return t;
            MarketStore.Trigger n = new MarketStore.Trigger(t.id(), t.account(), t.op(), t.threshold(), t.cost(), t.mode(),
                    t.cooldownMs(), t.command(), t.fired(), t.lastFired(), true, t.enabled());
            m.store().upsertTrigger(c, n);
            return n;
        }).thenAccept(Triggers::replace);
    }

    private static void replace(MarketStore.Trigger t) {
        if (t == null) return;
        for (int i = 0; i < cache.size(); i++) {
            if (cache.get(i).id().equals(t.id())) {
                cache.set(i, t);
                return;
            }
        }
    }

    private record Fired(MarketStore.Trigger trigger, long balanceAfter) {}

    private static void fire(MinecraftServer server, Market m, String id) {
        m.database().submit(c -> {
            long now = System.currentTimeMillis();
            MarketStore.Trigger t = m.store().lockTrigger(c, id);
            if (t == null || !t.enabled()) return null;
            long bal = m.store().lockAccount(c, t.account());
            if (!t.test(bal)) return null;
            boolean ok = switch (t.mode()) {
                case "once" -> t.fired() == 0;
                default -> t.cooldownMs() > 0 ? now - t.lastFired() >= t.cooldownMs() : t.armed();
            };
            if (!ok) return null;
            if (t.cost() > 0 && bal < t.cost()) return null;
            long after = bal;
            if (t.cost() > 0) after = m.store().addAccount(c, t.account(), -t.cost());
            MarketStore.Trigger n = new MarketStore.Trigger(t.id(), t.account(), t.op(), t.threshold(), t.cost(), t.mode(),
                    t.cooldownMs(), t.command(), t.fired() + 1, now, false, !"once".equals(t.mode()) && t.enabled());
            m.store().upsertTrigger(c, n);
            m.store().insertTx(c, new MarketStore.Tx(now, m.settings().serverId(), null, "trigger:" + t.id(), "trigger",
                    "$account:" + t.account(), 0, -t.cost(), 0, after));
            return new Fired(n, after);
        }).thenAccept(f -> {
            if (f == null) return;
            replace(f.trigger());
            if (f.trigger().cost() > 0) m.cacheAccount(f.trigger().account(), f.balanceAfter());
            server.execute(() -> run(server, f));
        }).exceptionally(t -> {
            FreeTrade.LOG.error("Trigger {} failed", id, t);
            return null;
        });
    }

    private static void run(MinecraftServer server, Fired f) {
        MarketStore.Trigger t = f.trigger();
        String cmd = t.command()
                .replace("{id}", t.id())
                .replace("{account}", t.account())
                .replace("{balance}", Money.format(f.balanceAfter()))
                .replace("{cost}", Money.format(t.cost()))
                .replace("{count}", Integer.toString(t.fired()));
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        FreeTrade.LOG.info("Trigger {} fired: {}", t.id(), cmd);
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack().withPermission(4), cmd);
    }
}
