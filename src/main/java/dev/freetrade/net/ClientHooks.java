package dev.freetrade.net;

import java.util.ArrayList;
import java.util.List;

import dev.freetrade.currency.Currency;

/**
 * Indirection so common code never loads client classes: the client module installs the real handlers.
 */
public final class ClientHooks {
    public interface Handler {
        void onCatalog(FreeTradeNetwork.CatalogPayload p);
    }

    private static Handler handler = p -> {
    };

    /** Latest catalog received (client side). */
    public static volatile FreeTradeNetwork.CatalogPayload lastCatalog;
    public static volatile boolean bankAvailable;

    private ClientHooks() {}

    public static void install(Handler h) {
        handler = h;
    }

    static void onCurrency(FreeTradeNetwork.CurrencySync p) {
        List<Currency.Denomination> list = new ArrayList<>();
        for (int i = 0; i < p.items().size(); i++) {
            var item = dev.freetrade.util.ItemKeys.item(p.items().get(i));
            if (item != net.minecraft.world.item.Items.AIR) list.add(new Currency.Denomination(item, p.values().get(i)));
        }
        Currency.setFromServer(list);
        bankAvailable = p.bank();
    }

    static void onCatalog(FreeTradeNetwork.CatalogPayload p) {
        lastCatalog = p;
        handler.onCatalog(p);
    }
}
