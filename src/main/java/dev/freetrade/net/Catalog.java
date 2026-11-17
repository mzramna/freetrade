package dev.freetrade.net;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.List;

import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.db.MarketStore;
import dev.freetrade.market.Money;
import dev.freetrade.market.Market;
import dev.freetrade.util.ItemKeys;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Items;

/** Snapshot of the association's catalog for the guide book and the representative's buy tab. */
public final class Catalog {
    /** sell/buy: whole U.M. (as milli) for one lot (buy -1 when out of stock); lot: units traded together (1 unless one unit is worth less than 1 U.M.). */
    public record Entry(String item, long stock, long sell, long buy, int lot, boolean declared) {
        public static final StreamCodec<ByteBuf, Entry> CODEC = StreamCodec.of((buf, e) -> {
            ByteBufCodecs.STRING_UTF8.encode(buf, e.item);
            ByteBufCodecs.VAR_LONG.encode(buf, e.stock);
            ByteBufCodecs.VAR_LONG.encode(buf, e.sell);
            ByteBufCodecs.VAR_LONG.encode(buf, e.buy + 1);
            ByteBufCodecs.VAR_INT.encode(buf, e.lot);
            ByteBufCodecs.BOOL.encode(buf, e.declared);
        }, buf -> new Entry(ByteBufCodecs.STRING_UTF8.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf),
                ByteBufCodecs.VAR_LONG.decode(buf), ByteBufCodecs.VAR_LONG.decode(buf) - 1, ByteBufCodecs.VAR_INT.decode(buf),
                ByteBufCodecs.BOOL.decode(buf)));
    }

    /** Live settings of the running server, shown by the guide book (never the defaults). */
    public record Header(double taxSell, double taxBuy, long treasury, boolean treasuryInfinite, String formula,
                         Map<String, String> config) {
        public static final StreamCodec<ByteBuf, Header> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Header::taxSell,
                ByteBufCodecs.DOUBLE, Header::taxBuy,
                ByteBufCodecs.VAR_LONG, Header::treasury,
                ByteBufCodecs.BOOL, Header::treasuryInfinite,
                ByteBufCodecs.STRING_UTF8, Header::formula,
                ByteBufCodecs.map(HashMap::new, ByteBufCodecs.STRING_UTF8, ByteBufCodecs.STRING_UTF8), Header::config,
                Header::new);

        public String get(String key) {
            return config.getOrDefault(key, "?");
        }
    }

    private Catalog() {}

    public static Header header(Market m) {
        Map<String, String> c = new HashMap<>();
        var st = m.settings();
        var p = m.engine().params();
        c.put("formula", p.expression().source());
        c.put("ceiling", p.ceilingUm() < 0 ? "-1" : Money.format(Money.ofDouble(p.ceilingUm())));
        c.put("min_unit", Money.format(Money.ofDouble(p.minUnitUm())));
        c.put("reference", Long.toString(st.defaultReference()));
        c.put("initial_stock", FreeTradeConfig.initialStock.name());
        c.put("dynamic", Boolean.toString(st.dynamicPropagation()));
        c.put("labor", pct(st.laborPercent()));
        c.put("damaged", Boolean.toString(FreeTradeConfig.damagedItems));
        c.put("tax_sell", pct(st.taxSellPercent()));
        c.put("tax_buy", pct(st.taxBuyPercent()));
        c.put("treasury_infinite", Boolean.toString(m.treasuryInfinite()));
        c.put("treasury", Money.format(m.account(MarketStore.TREASURY)));
        var b = st.backoff();
        c.put("backoff_enabled", Boolean.toString(b.enabled()));
        c.put("backoff_threshold", Integer.toString(b.threshold()));
        c.put("backoff_window", Long.toString(b.windowMs() / 1000));
        c.put("backoff_percent", pct(b.percentPerLevel()));
        c.put("backoff_max", Integer.toString(b.maxLevel()));
        c.put("machine_interval", String.format(java.util.Locale.ROOT, "%.2f", FreeTradeConfig.machineIntervalTicks / 20.0));
        c.put("machine_batch", Integer.toString(FreeTradeConfig.machineBatch));
        c.put("villager_stacks", Integer.toString(Math.min(27, FreeTradeConfig.villagerMaxStacks)));
        c.put("database", FreeTradeConfig.dbType.name());
        return new Header(st.taxSellPercent(), st.taxBuyPercent(), m.account(MarketStore.TREASURY), m.treasuryInfinite(),
                p.expression().source(), c);
    }

    private static String pct(double v) {
        String s = String.format(java.util.Locale.ROOT, "%.2f", v);
        s = s.replaceAll("0+$", "").replaceAll("\\.$", "");
        return s + "%";
    }

    public static List<Entry> build(Market m) {
        List<Entry> out = new ArrayList<>();
        var snap = m.snapshot();
        List<String> ids = new ArrayList<>(snap.declared().keySet());
        ids.addAll(snap.derived().keySet());
        for (String id : m.forcedPrices().keySet()) if (!ids.contains(id)) ids.add(id);
        for (String id : ids) {
            if (!m.isTradeable(id) || ItemKeys.item(id) == Items.AIR) continue;
            long stock = m.stock(id);
            long lot = m.lotSize(id);
            long sell = m.quoteSell(id, lot, 1.0);
            long buy = stock >= lot ? m.quoteBuy(id, lot, 1.0) : -1;
            out.add(new Entry(id, stock, sell, buy, (int) Math.max(-1, Math.min(Integer.MAX_VALUE, lot)),
                    snap.declared().containsKey(id)));
        }
        return out;
    }
}
