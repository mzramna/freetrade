package dev.freetrade;

import com.mojang.logging.LogUtils;

import dev.freetrade.block.MarketStorageBlockEntity;
import dev.freetrade.command.FreeTradeCommand;
import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.currency.Currency;
import dev.freetrade.net.FreeTradeNetwork;
import dev.freetrade.registry.ModRegistries;
import dev.freetrade.server.FreeTradeServer;
import dev.freetrade.villager.TraderInteraction;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

@Mod(FreeTrade.MODID)
public class FreeTrade {
    public static final String MODID = "freetrade";
    public static final Logger LOG = LogUtils.getLogger();

    public FreeTrade(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, FreeTradeConfig.SPEC);
        ModRegistries.register(modBus);
        modBus.addListener(FreeTrade::onConfigLoad);
        modBus.addListener(FreeTrade::onConfigReload);
        modBus.addListener(FreeTradeNetwork::register);
        modBus.addListener(FreeTrade::onCapabilities);
        FreeTradeServer.register();
        dev.freetrade.gametest.FreeTradeGameTests.register(modBus);
        TraderInteraction.register();
        NeoForge.EVENT_BUS.addListener(FreeTradeCommand::register);
        NeoForge.EVENT_BUS.addListener(FreeTrade::onLogin);
    }

    private static void onCapabilities(RegisterCapabilitiesEvent event) {
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModRegistries.SELL_POINT_BE.get(),
                MarketStorageBlockEntity::itemHandler);
        event.registerBlockEntity(Capabilities.Item.BLOCK, ModRegistries.AUTO_BUYER_BE.get(),
                MarketStorageBlockEntity::itemHandler);
    }

    private static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer sp) FreeTradeNetwork.syncCurrency(sp);
    }

    private static void onConfigLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getSpec() != FreeTradeConfig.SPEC) return;
        FreeTradeConfig.bake();
        Currency.rebuild();
    }

    private static void onConfigReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() != FreeTradeConfig.SPEC) return;
        FreeTradeConfig.bake();
        Currency.rebuild();
        FreeTradeServer.applyConfig();
        var server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) server.execute(() -> FreeTradeNetwork.syncCurrencyAll(server));
    }
}
