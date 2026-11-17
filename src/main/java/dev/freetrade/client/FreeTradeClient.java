package dev.freetrade.client;

import dev.freetrade.FreeTrade;
import dev.freetrade.registry.ModRegistries;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterMenuScreensEvent;

/** Client entrypoint: screens, the guide/catalog hooks and the price tooltip. */
@Mod(value = FreeTrade.MODID, dist = Dist.CLIENT)
public final class FreeTradeClient {
    public FreeTradeClient(IEventBus modBus, ModContainer container) {
        modBus.addListener(FreeTradeClient::onScreens);
        GuideClient.init(modBus);
    }

    private static void onScreens(RegisterMenuScreensEvent event) {
        event.register(ModRegistries.SELL_POINT_MENU.get(), MachineScreen<dev.freetrade.menu.SellPointMenu>::new);
        event.register(ModRegistries.AUTO_BUYER_MENU.get(), MachineScreen<dev.freetrade.menu.AutoBuyerMenu>::new);
        event.register(ModRegistries.TRADER_MENU.get(), TraderScreen::new);
    }
}
