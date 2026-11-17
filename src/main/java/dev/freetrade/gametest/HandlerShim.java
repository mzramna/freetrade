package dev.freetrade.gametest;

import dev.freetrade.block.MarketStorageBlockEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/** Test helper: the old slot-style calls on top of NeoForge's transactional transfer API. */
final class HandlerShim {
    private final ResourceHandler<ItemResource> h;

    private HandlerShim(ResourceHandler<ItemResource> h) {
        this.h = h;
    }

    static HandlerShim of(MarketStorageBlockEntity be) {
        return new HandlerShim(be.itemHandler(null));
    }

    /** Returns what was NOT inserted. */
    ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        try (Transaction tx = Transaction.openRoot()) {
            int n = h.insert(slot, ItemResource.of(stack), stack.getCount(), tx);
            if (!simulate) tx.commit();
            return stack.copyWithCount(stack.getCount() - n);
        }
    }

    ItemStack getStackInSlot(int slot) {
        ItemResource r = h.getResource(slot);
        return r.isEmpty() ? ItemStack.EMPTY : r.toStack((int) Math.min(64, h.getAmountAsLong(slot)));
    }

    ItemStack extractItem(int slot, int amount, boolean simulate) {
        ItemResource r = h.getResource(slot);
        if (r.isEmpty()) return ItemStack.EMPTY;
        try (Transaction tx = Transaction.openRoot()) {
            int n = h.extract(slot, r, amount, tx);
            if (!simulate) tx.commit();
            return n <= 0 ? ItemStack.EMPTY : r.toStack(n);
        }
    }
}
