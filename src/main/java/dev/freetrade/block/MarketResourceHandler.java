package dev.freetrade.block;

import dev.freetrade.currency.Currency;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.transfer.ResourceHandler;
import net.neoforged.neoforge.transfer.item.ItemResource;
import net.neoforged.neoforge.transfer.transaction.SnapshotJournal;
import net.neoforged.neoforge.transfer.transaction.TransactionContext;

/**
 * Automation access (hoppers, pipes) to a Sell Point / Auto Buyer through NeoForge's transfer API.
 * Index 0 = input (money becomes balance; on a Sell Point also the configured item).
 * Index 1 = output, only while powered by redstone: the selected money item (Sell Point) or the bought items
 * (Auto Buyer). Changes are journaled so aborted transactions roll back.
 */
final class MarketResourceHandler implements ResourceHandler<ItemResource> {
    private final MarketStorageBlockEntity be;
    private final boolean sellPoint;

    private final SnapshotJournal<long[]> journal = new SnapshotJournal<>() {
        @Override
        protected long[] createSnapshot() {
            return new long[]{be.stored, be.balance};
        }

        @Override
        protected void revertToSnapshot(long[] snapshot) {
            be.stored = snapshot[0];
            be.balance = snapshot[1];
        }

        @Override
        protected void onRootCommit(long[] originalState) {
            be.setChanged();
        }
    };

    MarketResourceHandler(MarketStorageBlockEntity be, boolean sellPoint) {
        this.be = be;
        this.sellPoint = sellPoint;
    }

    @Override
    public int size() {
        return 2;
    }

    private ItemResource output() {
        if (!be.powered || be.pending) return ItemResource.EMPTY;
        if (sellPoint) {
            Item c = be.selectedCurrency();
            if (c == null || be.balance < Currency.valueOf(c)) return ItemResource.EMPTY;
            return ItemResource.of(c);
        }
        if (be.stored <= 0) return ItemResource.EMPTY;
        return ItemResource.of(be.targetItem());
    }

    private long outputAmount() {
        if (output().isEmpty()) return 0;
        if (sellPoint) {
            Item c = be.selectedCurrency();
            return c == null ? 0 : be.balance / Currency.valueOf(c);
        }
        return be.stored;
    }

    @Override
    public ItemResource getResource(int index) {
        return index == 1 ? output() : ItemResource.EMPTY;
    }

    @Override
    public long getAmountAsLong(int index) {
        return index == 1 ? outputAmount() : 0;
    }

    @Override
    public long getCapacityAsLong(int index, ItemResource resource) {
        if (index == 0) return isValid(0, resource) ? Integer.MAX_VALUE : 0;
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean isValid(int index, ItemResource resource) {
        if (index != 0 || resource.isEmpty()) return false;
        if (Currency.isCurrency(resource.getItem())) return resource.isComponentsPatchEmpty();
        return sellPoint && !be.target.isEmpty() && resource.is(be.targetItem()) && resource.isComponentsPatchEmpty();
    }

    @Override
    public int insert(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (amount <= 0 || !isValid(index, resource)) return 0;
        long value = Currency.valueOf(resource.getItem());
        if (value > 0) {
            journal.updateSnapshots(transaction);
            be.balance = dev.freetrade.market.Money.saturatingAdd(be.balance, value * amount);
            return amount;
        }
        int n = (int) Math.min(amount, be.space());
        if (n <= 0) return 0;
        journal.updateSnapshots(transaction);
        be.stored += n;
        return n;
    }

    @Override
    public int extract(int index, ItemResource resource, int amount, TransactionContext transaction) {
        if (index != 1 || amount <= 0 || resource.isEmpty()) return 0;
        ItemResource out = output();
        if (out.isEmpty() || !out.equals(resource)) return 0;
        int n = (int) Math.min(amount, outputAmount());
        if (n <= 0) return 0;
        journal.updateSnapshots(transaction);
        if (sellPoint) be.balance -= n * Currency.valueOf(resource.getItem());
        else be.stored -= n;
        return n;
    }

    /** For tests / callers without a transaction. */
    static ItemStack toStack(ItemResource r, int n) {
        return r.toStack(n);
    }
}
