package dev.freetrade.net;

import java.util.ArrayList;
import java.util.List;

import dev.freetrade.db.MarketStore;
import dev.freetrade.market.Market;
import dev.freetrade.util.ItemKeys;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Items;

/** Snapshot of the association's catalog for the guide book and the representative's buy tab. */
public final class Catalog {
    /** sell/buy: milli-U.M. for one unit (buy -1 when out of stock); lot: units per 1 U.M. when one unit is worth less. */
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

    public record Header(double taxSell, double taxBuy, long treasury, boolean treasuryInfinite, String formula) {
        public static final StreamCodec<ByteBuf, Header> CODEC = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, Header::taxSell,
                ByteBufCodecs.DOUBLE, Header::taxBuy,
                ByteBufCodecs.VAR_LONG, Header::treasury,
                ByteBufCodecs.BOOL, Header::treasuryInfinite,
                ByteBufCodecs.STRING_UTF8, Header::formula,
                Header::new);
    }

    private Catalog() {}

    public static Header header(Market m) {
        return new Header(m.settings().taxSellPercent(), m.settings().taxBuyPercent(), m.account(MarketStore.TREASURY),
                m.treasuryInfinite(), m.engine().params().expression().source());
    }

    public static List<Entry> build(Market m) {
        List<Entry> out = new ArrayList<>();
        var snap = m.snapshot();
        List<String> ids = new ArrayList<>(snap.declared().keySet());
        ids.addAll(snap.derived().keySet());
        for (String id : ids) {
            if (!m.isTradeable(id) || ItemKeys.item(id) == Items.AIR) continue;
            long stock = m.stock(id);
            long sell = m.quoteSellNet(id, 1, 1.0);
            long buy = stock > 0 ? m.quoteBuy(id, 1, 1.0) : -1;
            long lot = sell >= 1000 ? 1 : m.engine().unitsPerOneUm(m.baseValue(id), m.reference(id), stock, 100_000);
            out.add(new Entry(id, stock, sell, buy, (int) Math.max(-1, Math.min(Integer.MAX_VALUE, lot)),
                    snap.declared().containsKey(id)));
        }
        return out;
    }
}
