package dev.freetrade.client;

import dev.freetrade.net.ClientHooks;
import dev.freetrade.net.FreeTradeNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;

/** Client side of the guide book: opens {@link GuideScreen}, refreshes it, and adds the price tooltips. */
public final class GuideClient {
    private GuideClient() {}

    public static void init(IEventBus modBus) {
        ClientHooks.install(GuideClient::onCatalog);
        NeoForge.EVENT_BUS.addListener(PriceTooltip::onTooltip);
    }

    private static void onCatalog(FreeTradeNetwork.CatalogPayload p) {
        Minecraft mc = Minecraft.getInstance();
        // only payloads meant for the book reach it (the book's catalog may be restricted by the server)
        if (!p.openGuide()) return;
        if (mc.screen instanceof GuideScreen gs) gs.update(p);
        else mc.setScreen(new GuideScreen(p));
    }
}
