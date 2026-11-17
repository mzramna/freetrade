package dev.freetrade.block;

import javax.annotation.Nullable;

import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.currency.Currency;
import dev.freetrade.market.Market;
import dev.freetrade.registry.ModRegistries;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Sell Point: stores one item and, while powered by redstone, sells it to the association. The money goes to
 * the block's balance; the owner withdraws it in any money item. Money items put in become balance (change
 * machine). Automation can pull the selected money item only while powered.
 */
public class SellPointBlockEntity extends MarketStorageBlockEntity {
    public SellPointBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistries.SELL_POINT_BE.get(), pos, state);
    }

    @Override
    protected long computeUnitPrice(Market market) {
        return market.quoteSellNet(target, 1, 1.0);
    }

    @Override
    protected void work(Market market) {
        if (!powered) {
            status = stored > 0 ? STATUS_WAITING_REDSTONE : STATUS_IDLE;
            return;
        }
        if (stored <= 0) {
            status = STATUS_IDLE;
            return;
        }
        long n = Math.min(stored, FreeTradeConfig.machineBatch);
        String item = target;
        stored -= n; // escrow while the trade runs
        pending = true;
        setChanged();
        market.sell(tradeContext("sell_point"), item, n, 1.0, false).thenAccept(res -> onServer(() -> {
            pending = false;
            long back = n - (res.ok() ? res.units() : 0);
            if (isRemoved() && level != null) {
                // block broken meanwhile: hand out what belongs to the owner
                ItemStack rest = new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        net.minecraft.resources.ResourceLocation.parse(item)), (int) Math.min(Integer.MAX_VALUE, back));
                if (!rest.isEmpty()) Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), rest);
                for (ItemStack s : Currency.change(res.ok() ? res.payoutMilli() : 0).stacks()) {
                    Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), s);
                }
                return;
            }
            stored += back;
            if (res.ok()) balance += res.payoutMilli();
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
            if (slot != 1 || !powered) return ItemStack.EMPTY;
            Item c = selectedCurrency();
            return c == null ? ItemStack.EMPTY : withdrawCurrency(c, Integer.MAX_VALUE, true);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || stack.isEmpty()) return stack;
            if (Currency.isCurrency(stack)) return depositCurrency(stack, simulate);
            return insertStored(stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot != 1 || !powered || pending) return ItemStack.EMPTY;
            Item c = selectedCurrency();
            return c == null ? ItemStack.EMPTY : withdrawCurrency(c, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && (Currency.isCurrency(stack) || (!target.isEmpty() && insertStored(stack, true).getCount() < stack.getCount()));
        }
    };

    @Override
    public IItemHandler itemHandler(@Nullable Direction side) {
        return handler;
    }
}
