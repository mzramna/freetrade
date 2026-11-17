package dev.freetrade.villager;

import dev.freetrade.menu.TraderMenu;
import dev.freetrade.net.FreeTradeNetwork;
import dev.freetrade.registry.ModRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.npc.villager.Villager;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Right-clicking a trade representative opens the Free Trade window instead of vanilla trading. */
public final class TraderInteraction {
    private TraderInteraction() {}

    public static void register() {
        NeoForge.EVENT_BUS.addListener(TraderInteraction::onInteract);
    }

    private static void onInteract(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getTarget() instanceof Villager v)) return;
        if (!v.getVillagerData().profession().is(ModRegistries.TRADE_REPRESENTATIVE.getKey()) || v.isBaby()) return;
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.SUCCESS);
        if (!(event.getEntity() instanceof ServerPlayer sp)) return;
        if (v.isTrading() && v.getTradingPlayer() != sp) return;
        v.setTradingPlayer(sp);
        sp.openMenu(new SimpleMenuProvider((id, inv, p) -> new TraderMenu(id, inv, v),
                Component.translatable("entity.freetrade.trade_representative")));
        FreeTradeNetwork.sendCatalog(sp, false);
    }
}
