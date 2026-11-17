package dev.freetrade.util;

import java.util.List;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

public final class Give {
    private Give() {}

    /** Gives stacks to the player, dropping what does not fit. */
    public static void toPlayer(ServerPlayer player, List<ItemStack> stacks) {
        for (ItemStack s : stacks) {
            ItemStack copy = s.copy();
            if (!player.getInventory().add(copy) && !copy.isEmpty()) player.drop(copy, false);
        }
    }
}
