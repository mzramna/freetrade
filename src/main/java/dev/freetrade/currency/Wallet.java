package dev.freetrade.currency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.util.Give;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/** Paying with the money items in a player's inventory (or the Lightman's Currency bank). */
public final class Wallet {
    private Wallet() {}

    public static long inventoryValue(Inventory inv) {
        long v = 0;
        for (int i = 0; i < inv.getContainerSize(); i++) v += Currency.valueOf(inv.getItem(i));
        return v;
    }

    public static boolean bankAvailable() {
        return FreeTradeConfig.lightmansBank && Currency.lightmansLoaded();
    }

    public static long bankValue(ServerPlayer p) {
        if (!bankAvailable()) return -1;
        try {
            return LightmansCompat.bankBalance(p, FreeTradeConfig.lightmansRate);
        } catch (Throwable t) {
            return -1;
        }
    }

    /**
     * Removes money items worth at least {@code amount}, smallest items first. Returns the value taken
     * (0 when the inventory does not hold enough; nothing is removed then).
     */
    public static long takeFromInventory(Inventory inv, long amount) {
        if (amount <= 0) return 0;
        if (inventoryValue(inv) < amount) return 0;
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getContainerSize(); i++) if (Currency.valueOf(inv.getItem(i)) > 0) slots.add(i);
        slots.sort(Comparator.comparingLong(i -> Currency.valueOf(inv.getItem(i).getItem())));
        long taken = 0;
        for (int slot : slots) {
            ItemStack s = inv.getItem(slot);
            long each = Currency.valueOf(s.getItem());
            while (!s.isEmpty() && taken < amount) {
                s.shrink(1);
                taken += each;
            }
            inv.setItem(slot, s.isEmpty() ? ItemStack.EMPTY : s);
            if (taken >= amount) break;
        }
        inv.setChanged();
        return taken;
    }

    /** Pays out as money items (largest first). Returns the unrepresentable remainder (milli). */
    public static long giveChange(ServerPlayer p, long milli) {
        var change = Currency.change(milli);
        Give.toPlayer(p, change.stacks());
        return change.remainderMilli();
    }

    /** Withdraws from the bank; returns milli taken or -1. */
    public static long takeFromBank(ServerPlayer p, long milli) {
        try {
            return LightmansCompat.withdraw(p, milli, FreeTradeConfig.lightmansRate);
        } catch (Throwable t) {
            return -1;
        }
    }

    /** Deposits into the bank; returns what could not be deposited (milli). */
    public static long depositToBank(ServerPlayer p, long milli) {
        try {
            return milli - LightmansCompat.deposit(p, milli, FreeTradeConfig.lightmansRate);
        } catch (Throwable t) {
            return milli;
        }
    }
}
