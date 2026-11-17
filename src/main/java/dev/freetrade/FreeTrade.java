package dev.freetrade;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;

@Mod(FreeTrade.MODID)
public class FreeTrade {
    public static final String MODID = "freetrade";
    public static final Logger LOG = LogUtils.getLogger();

    public FreeTrade(IEventBus modBus, ModContainer container) {
    }
}
