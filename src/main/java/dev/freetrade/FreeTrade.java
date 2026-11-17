package dev.freetrade;

import com.mojang.logging.LogUtils;

import dev.freetrade.command.FreeTradeCommand;
import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.currency.Currency;
import dev.freetrade.server.FreeTradeServer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(FreeTrade.MODID)
public class FreeTrade {
    public static final String MODID = "freetrade";
    public static final Logger LOG = LogUtils.getLogger();

    public FreeTrade(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, FreeTradeConfig.SPEC);
        modBus.addListener(FreeTrade::onConfigLoad);
        modBus.addListener(FreeTrade::onConfigReload);
        FreeTradeServer.register();
        NeoForge.EVENT_BUS.addListener(FreeTradeCommand::register);
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
    }
}
