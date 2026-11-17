package dev.freetrade.block;

import javax.annotation.Nullable;

import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.currency.Currency;
import dev.freetrade.market.Market;
import dev.freetrade.market.Money;
import dev.freetrade.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Auto Buyer (also the trade representative's workstation): money items put in become balance, and the block
 * buys its configured item from the association while there is balance, stock and space. Automation can pull the
 * bought items only while powered by redstone; the owner can always take them (and the leftover balance) by hand.
 */
public class AutoBuyerBlockEntity extends MarketStorageBlockEntity {
    public AutoBuyerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.AUTO_BUYER_BE.get(), pos, state);
    }

    @Override
    protected long computeUnitPrice(Market market) {
        return market.quoteBuy(target, market.lotSize(target), 1.0);
    }

    @Override
    protected void work(Market market) {
        long space = space();
        if (space <= 0) {
            status = STATUS_FULL;
            return;
        }
        if (balance <= 0) {
            status = STATUS_IDLE;
            return;
        }
        if (market.stock(target) <= 0) {
            status = STATUS_NO_STOCK;
            return;
        }
        long one = market.quoteBuy(target, market.lotSize(target), 1.0);
        if (one < 0 || one > balance) {
            status = STATUS_NO_FUNDS;
            return;
        }
        long budget = balance;
        long want = Math.min(space, Math.max(FreeTradeConfig.machineBatch, market.lotSize(target)));
        String item = target;
        balance = 0; // escrow while the trade runs
        pending = true;
        setChanged();
        market.buy(tradeContext("auto_buyer"), item, want, budget, false).thenAccept(res -> onServer(() -> {
            pending = false;
            long left = res.ok() ? budget - res.costMilli() : budget;
            if (isRemoved() && level != null) {
                for (ItemStack s : Currency.change(left).stacks()) {
                    Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), s);
                }
                if (res.ok()) {
                    ItemStack bought = dev.freetrade.util.ItemKeys.stack(item, (int) res.units());
                    Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), bought);
                }
                return;
            }
            balance = Money.saturatingAdd(balance, left);
            if (res.ok()) stored += res.units();
            status = statusOf(res.error());
            refreshPrice();
            setChanged();
        }));
    }

    private final IItemHandler handler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            if (slot != 1 || !powered || pending || stored <= 0) return ItemStack.EMPTY;
            ItemStack s = new ItemStack(targetItem());
            s.setCount((int) Math.min(s.getMaxStackSize(), stored));
            return s;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty() || !Currency.isCurrency(stack)) return stack;
            return depositCurrency(stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1 || !powered || pending || stored <= 0) return ItemStack.EMPTY;
            ItemStack s = new ItemStack(targetItem());
            int n = (int) Math.min(Math.min(amount, s.getMaxStackSize()), stored);
            if (n <= 0) return ItemStack.EMPTY;
            if (!simulate) {
                stored -= n;
                setChanged();
            }
            s.setCount(n);
            return s;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && Currency.isCurrency(stack);
        }
    };

    @Override
    public IItemHandler itemHandler(@Nullable Direction side) {
        return handler;
    }
}
