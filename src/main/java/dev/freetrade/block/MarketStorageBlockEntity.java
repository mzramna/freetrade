package dev.freetrade.block;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import javax.annotation.Nullable;

import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.currency.Currency;
import dev.freetrade.market.Market;
import dev.freetrade.market.Money;
import dev.freetrade.server.FreeTradeServer;
import dev.freetrade.util.ItemKeys;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shared logic of the Sell Point and the Auto Buyer: one configured item kept as a plain counter, a money
 * balance in milli-U.M., capacity = one double chest of the item x (1 + adjacent chests/barrels, max 5),
 * redstone state and an owner. Market calls are asynchronous; {@link #pending} prevents overlapping trades.
 */
public abstract class MarketStorageBlockEntity extends BlockEntity {
    public static final TagKey<Block> STORAGE_UPGRADES =
            TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("freetrade", "storage_upgrades"));
    public static final int MAX_UPGRADES = 5;
    public static final int BASE_STACKS = 54;

    // Status codes shown in the GUI
    public static final int STATUS_IDLE = 0, STATUS_OK = 1, STATUS_NO_TARGET = 2, STATUS_NOT_TRADEABLE = 3,
            STATUS_TREASURY = 4, STATUS_NO_STOCK = 5, STATUS_NO_FUNDS = 6, STATUS_OFFLINE = 7, STATUS_FULL = 8,
            STATUS_ERROR = 9, STATUS_WAITING_REDSTONE = 10;

    @Nullable protected UUID owner;
    protected String ownerName = "";
    protected String target = "";
    protected long stored;
    protected long balance;
    protected int currencyIndex;
    protected int upgrades;
    protected boolean powered;
    protected int status = STATUS_IDLE;
    protected long unitPrice;
    protected transient boolean pending;
    private int ticks;

    protected MarketStorageBlockEntity(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    // ------------------------------------------------------------------ accessors

    public void setOwner(Player player) {
        this.owner = player.getUUID();
        this.ownerName = player.getGameProfile().name();
        setChanged();
    }

    public boolean canAccess(Player player) {
        return owner == null || owner.equals(player.getUUID()) || player.permissions().hasPermission(net.minecraft.server.permissions.Permissions.COMMANDS_GAMEMASTER);
    }

    public String ownerName() {
        return ownerName;
    }

    public String target() {
        return target;
    }

    public Item targetItem() {
        return target.isEmpty() ? Items.AIR : ItemKeys.item(target);
    }

    public long stored() {
        return stored;
    }

    public long balance() {
        return balance;
    }

    public boolean powered() {
        return powered;
    }

    public int upgrades() {
        return upgrades;
    }

    public long capacity() {
        Item i = targetItem();
        if (i == Items.AIR) return 0;
        return (long) new ItemStack(i).getMaxStackSize() * BASE_STACKS * (1 + upgrades);
    }

    public long space() {
        return Math.max(0, capacity() - stored);
    }

    @Nullable
    public Item selectedCurrency() {
        var ds = Currency.denominations();
        if (ds.isEmpty()) return null;
        return ds.get(Math.floorMod(currencyIndex, ds.size())).item();
    }

    public void cycleCurrency(int delta) {
        int n = Currency.denominations().size();
        if (n == 0) return;
        currencyIndex = Math.floorMod(currencyIndex + delta, n);
        setChanged();
    }

    /** Changes the configured item. Only allowed while nothing of the old item is stored. */
    public boolean setTarget(ItemStack stack) {
        String id = stack.isEmpty() ? "" : ItemKeys.id(stack);
        if (id.equals(target)) return true;
        if (stored > 0 || pending) return false;
        if (!id.isEmpty() && !acceptsAsTarget(stack)) return false;
        target = id;
        refreshPrice();
        sync();
        return true;
    }

    protected boolean acceptsAsTarget(ItemStack stack) {
        return !Currency.isCurrency(stack);
    }

    // ------------------------------------------------------------------ money

    /** Converts money items into balance. Returns what was not money. */
    public ItemStack depositCurrency(ItemStack stack, boolean simulate) {
        long v = Currency.valueOf(stack);
        if (v <= 0) return stack;
        if (!simulate) {
            balance = Money.saturatingAdd(balance, v);
            setChanged();
        }
        return ItemStack.EMPTY;
    }

    /** Takes up to {@code max} items of a money item, paid from the balance. */
    public ItemStack withdrawCurrency(Item item, int max, boolean simulate) {
        long v = Currency.valueOf(item);
        if (v <= 0 || max <= 0) return ItemStack.EMPTY;
        int n = (int) Math.min(Math.min(max, new ItemStack(item).getMaxStackSize()), balance / v);
        if (n <= 0) return ItemStack.EMPTY;
        if (!simulate) {
            balance -= n * v;
            setChanged();
        }
        return new ItemStack(item, n);
    }

    /** Whole balance as change in the largest money items; the unrepresentable remainder stays. */
    public List<ItemStack> withdrawAll() {
        var change = Currency.change(balance);
        balance = change.remainderMilli();
        setChanged();
        return change.stacks();
    }

    /** Takes up to {@code max} stored items (manual withdrawal from the GUI). */
    public ItemStack takeStored(int max) {
        Item i = targetItem();
        if (i == Items.AIR || stored <= 0 || pending) return ItemStack.EMPTY;
        int n = (int) Math.min(Math.min(max, new ItemStack(i).getMaxStackSize()), stored);
        stored -= n;
        setChanged();
        return new ItemStack(i, n);
    }

    /** Adds plain target items to the counter. Returns what did not fit / was refused. */
    public ItemStack insertStored(ItemStack stack, boolean simulate) {
        if (stack.isEmpty() || target.isEmpty() || !ItemKeys.id(stack).equals(target)) return stack;
        if (!ItemKeys.isPlain(stack, false)) return stack;
        long n = Math.min(stack.getCount(), space());
        if (n <= 0) return stack;
        if (!simulate) {
            stored += n;
            setChanged();
        }
        ItemStack rest = stack.copy();
        rest.shrink((int) n);
        return rest;
    }

    // ------------------------------------------------------------------ ticking

    public static <T extends BlockEntity> void serverTick(Level level, BlockPos pos, BlockState state, T be) {
        if (be instanceof MarketStorageBlockEntity m) m.tick(level, pos);
    }

    private void tick(Level level, BlockPos pos) {
        ticks++;
        boolean p = level.hasNeighborSignal(pos);
        if (p != powered) {
            powered = p;
            setChanged();
        }
        if (ticks % 20 == 0) {
            int u = countUpgrades(level, pos);
            if (u != upgrades) {
                upgrades = u;
                setChanged();
            }
            refreshPrice();
        }
        if (ticks % Math.max(1, FreeTradeConfig.machineIntervalTicks) != 0 || pending) return;
        Market m = FreeTradeServer.market();
        if (m == null) {
            status = STATUS_OFFLINE;
            return;
        }
        if (target.isEmpty()) {
            status = STATUS_NO_TARGET;
            return;
        }
        if (!m.isTradeable(target)) {
            status = STATUS_NOT_TRADEABLE;
            return;
        }
        work(m);
    }

    static int countUpgrades(Level level, BlockPos pos) {
        int n = 0;
        for (Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).is(STORAGE_UPGRADES)) n++;
        }
        return Math.min(MAX_UPGRADES, n);
    }

    protected abstract void work(Market market);

    /** Price shown in the GUI (milli per unit). */
    protected abstract long computeUnitPrice(Market market);

    protected void refreshPrice() {
        Market m = FreeTradeServer.market();
        unitPrice = m == null || target.isEmpty() ? 0 : Math.max(0, computeUnitPrice(m));
    }

    protected Market.TradeContext tradeContext(String source) {
        return FreeTradeServer.context(owner == null ? null : owner.toString(), ownerName, source);
    }

    protected static int statusOf(Market.Error e) {
        return switch (e) {
            case NONE -> STATUS_OK;
            case NOT_TRADEABLE -> STATUS_NOT_TRADEABLE;
            case TREASURY_EMPTY -> STATUS_TREASURY;
            case NO_STOCK -> STATUS_NO_STOCK;
            case NO_FUNDS, TOO_FEW -> STATUS_NO_FUNDS;
            case DATABASE -> STATUS_ERROR;
        };
    }

    /** Runs a completion back on the server thread; drops the contents if the block is gone by then. */
    protected void onServer(Runnable r) {
        Level l = level;
        if (l == null || l.getServer() == null) return;
        l.getServer().execute(r);
    }

    // ------------------------------------------------------------------ drops

    /** Everything the block holds as item stacks (stored items + balance as money). */
    public List<ItemStack> contentsForDrop() {
        List<ItemStack> out = new ArrayList<>();
        Item i = targetItem();
        long left = stored;
        if (i != Items.AIR) {
            int max = new ItemStack(i).getMaxStackSize();
            while (left > 0) {
                int n = (int) Math.min(max, left);
                out.add(new ItemStack(i, n));
                left -= n;
            }
        }
        stored = 0;
        out.addAll(withdrawAll());
        return out;
    }

    public void dropContents(Level level, BlockPos pos) {
        for (ItemStack s : contentsForDrop()) Containers.dropItemStack(level, pos.getX(), pos.getY(), pos.getZ(), s);
    }

    // ------------------------------------------------------------------ hopper access

    public abstract net.neoforged.neoforge.transfer.ResourceHandler<net.neoforged.neoforge.transfer.item.ItemResource> itemHandler(@Nullable Direction side);

    // ------------------------------------------------------------------ GUI data (shorts)

    public static final int DATA_COUNT = 23;

    protected static int part(long v, int i) {
        return (int) ((v >>> (16 * i)) & 0xFFFF);
    }

    public static long join(ContainerData d, int base) {
        long v = 0;
        for (int i = 0; i < 4; i++) v |= ((long) (d.get(base + i) & 0xFFFF)) << (16 * i);
        return v;
    }

    public final ContainerData data = new ContainerData() {
        @Override
        public int get(int i) {
            if (i < 4) return part(stored, i);
            if (i < 8) return part(capacity(), i - 4);
            if (i < 12) return part(balance, i - 8);
            if (i < 16) return part(unitPrice, i - 12);
            if (i < 20) return part(0, i - 16);
            return switch (i) {
                case 20 -> currencyIndex;
                case 21 -> (powered ? 1 : 0) | (pending ? 2 : 0) | (upgrades << 2);
                case 22 -> status;
                default -> 0;
            };
        }

        @Override
        public void set(int i, int v) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(net.minecraft.world.level.storage.ValueOutput out) {
        super.saveAdditional(out);
        out.putString("owner", owner == null ? "" : owner.toString());
        out.putString("ownerName", ownerName);
        out.putString("target", target);
        out.putLong("stored", stored);
        out.putLong("balance", balance);
        out.putInt("currency", currencyIndex);
    }

    @Override
    protected void loadAdditional(net.minecraft.world.level.storage.ValueInput in) {
        super.loadAdditional(in);
        String o = in.getStringOr("owner", "");
        try {
            owner = o.isEmpty() ? null : UUID.fromString(o);
        } catch (IllegalArgumentException e) {
            owner = null;
        }
        ownerName = in.getStringOr("ownerName", "");
        target = in.getStringOr("target", "");
        stored = in.getLongOr("stored", stored);
        balance = in.getLongOr("balance", balance);
        currencyIndex = in.getIntOr("currency", currencyIndex);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        tag.putString("owner", owner == null ? "" : owner.toString());
        tag.putString("ownerName", ownerName);
        tag.putString("target", target);
        return tag;
    }

    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        if (level != null && !level.isClientSide()) dropContents(level, pos);
    }

    protected void sync() {
        setChanged();
        if (level != null && !level.isClientSide()) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public boolean stillValid(ServerPlayer p) {
        return !isRemoved() && p.distanceToSqr(worldPosition.getCenter()) <= 64;
    }
}
