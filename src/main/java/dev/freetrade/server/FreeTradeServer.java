package dev.freetrade.server;

import java.nio.file.Path;

import dev.freetrade.FreeTrade;
import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.db.Database;
import dev.freetrade.market.Market;
import dev.freetrade.valuation.Valuation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/** Owns the database and the market while a server (or singleplayer world) runs. */
public final class FreeTradeServer {
    private static volatile Market market;
    private static volatile MinecraftServer server;
    private static Database database;
    private static long ticks;

    private FreeTradeServer() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(FreeTradeServer::onStarting);
        NeoForge.EVENT_BUS.addListener(FreeTradeServer::onStopped);
        NeoForge.EVENT_BUS.addListener(FreeTradeServer::onTick);
    }

    /** Null while no server runs or when the database could not be opened. */
    public static Market market() {
        return market;
    }

    public static MinecraftServer server() {
        return server;
    }

    public static Market.Settings settings() {
        return new Market.Settings(FreeTradeConfig.referenceStock, FreeTradeConfig.dynamicPropagation,
                FreeTradeConfig.laborPercent, FreeTradeConfig.taxSellPercent, FreeTradeConfig.taxBuyPercent,
                FreeTradeConfig.treasuryInitial, FreeTradeConfig.backoff, FreeTradeConfig.serverId);
    }

    public static Market.TradeContext context(Player player, String source) {
        return new Market.TradeContext(player.getUUID().toString(), player.getGameProfile().getName(), source);
    }

    public static Market.TradeContext context(String ownerUuid, String ownerName, String source) {
        return new Market.TradeContext(ownerUuid, ownerName, source);
    }

    private static void onStarting(ServerStartingEvent event) {
        server = event.getServer();
        FreeTradeConfig.bake();
        try {
            database = switch (FreeTradeConfig.dbType) {
                case SQLITE -> {
                    Path p = Path.of(FreeTradeConfig.sqliteFile);
                    if (!p.isAbsolute()) p = server.getWorldPath(LevelResource.ROOT).resolve(p).normalize();
                    yield Database.sqlite(p.toString());
                }
                case POSTGRES -> Database.postgres(FreeTradeConfig.pgUrl, FreeTradeConfig.pgUser, FreeTradeConfig.pgPassword);
            };
            Market m = new Market(database, FreeTradeConfig.pricing, settings());
            Valuation.apply(m, Valuation.gather(server), false);
            m.addListener(mk -> {
                MinecraftServer s = server;
                if (s != null) s.execute(Triggers::checkSoon);
            });
            market = m;
            Triggers.load(m);
            FreeTrade.LOG.info("Free Trade market open ({})", FreeTradeConfig.dbType);
        } catch (Exception e) {
            FreeTrade.LOG.error("Free Trade could not open its database; trading is disabled until restart", e);
            market = null;
            if (database != null) database.close();
            database = null;
        }
    }

    private static void onStopped(ServerStoppedEvent event) {
        market = null;
        Triggers.clear();
        if (database != null) {
            database.close();
            database = null;
        }
        server = null;
    }

    /** Applies a reloaded config to the running market. */
    public static void applyConfig() {
        Market m = market;
        if (m != null) m.reconfigure(FreeTradeConfig.pricing, settings());
    }

    private static void onTick(ServerTickEvent.Post event) {
        Market m = market;
        if (m == null) return;
        ticks++;
        if (FreeTradeConfig.dbType == FreeTradeConfig.DbType.POSTGRES
                && ticks % (20L * FreeTradeConfig.cacheRefreshSeconds) == 0) {
            m.refreshAsync();
            Triggers.reloadAsync(m);
        }
        if (ticks % FreeTradeConfig.triggerIntervalTicks == 0) Triggers.check(event.getServer(), m);
        if (FreeTradeConfig.historyDays > 0 && ticks % (20L * 60 * 60) == 0) {
            long cutoff = System.currentTimeMillis() - FreeTradeConfig.historyDays * 86_400_000L;
            m.database().submit(c -> m.store().pruneHistory(c, cutoff));
        }
    }

    public static boolean isOp(ServerPlayer p) {
        return p.hasPermissions(2);
    }
}
