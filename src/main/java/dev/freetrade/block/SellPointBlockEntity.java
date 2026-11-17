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
        return market.quoteSell(target, market.lotSize(target), 1.0);
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
        long n = Math.min(stored, Math.max(FreeTradeConfig.machineBatch, market.lotSize(target)));
        if (market.sellUnits(target, n) <= 0) {
            status = STATUS_LOT;
            return;
        }
        String item = target;
        stored -= n; // escrow while the trade runs
        pending = true;
        setChanged();
        market.sell(tradeContext("sell_point"), item, n, 1.0).thenAccept(res -> onServer(() -> {
            pending = false;
            long back = n - (res.ok() ? res.units() : 0);
            if (isRemoved() && level != null) {
                // block broken meanwhile: hand out what belongs to the owner
                ItemStack rest = dev.freetrade.util.ItemKeys.stack(item, (int) Math.min(Integer.MAX_VALUE, back));
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

    private final MarketResourceHandler handler = new MarketResourceHandler(this, true);

    @Override
    public net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> itemHandler(@Nullable Direction side) {
        return handler;
    }
}
