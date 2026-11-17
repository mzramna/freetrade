package dev.freetrade.item;

import dev.freetrade.net.FreeTradeNetwork;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** The Trade Association guide: explains the mod and lists every item with its buy/sell price and stock. */
public class TradeGuideItem extends Item {
    public TradeGuideItem(Properties props) {
        super(props);
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer sp) FreeTradeNetwork.sendCatalog(sp, true);
        return InteractionResult.SUCCESS;
    }
}
