package dev.freetrade.menu;

import java.util.List;

import javax.annotation.Nullable;

import dev.freetrade.block.AutoBuyerBlockEntity;
import dev.freetrade.block.MarketStorageBlockEntity;
import dev.freetrade.currency.Currency;
import dev.freetrade.util.Give;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * GUI of the Sell Point / Auto Buyer. One real input slot (items/money put there are absorbed by the block),
 * buttons for the money selector and withdrawals, numbers synced through {@link ContainerData}. The configured
 * item is a ghost: set via {@link #setTarget} (screen click or JEI/EMI drag), never consumed.
 */
public abstract class MachineMenu extends AbstractContainerMenu {
    public static final int BTN_CURRENCY_UP = 0, BTN_CURRENCY_DOWN = 1, BTN_WITHDRAW_ONE = 2, BTN_WITHDRAW_STACK = 3,
            BTN_WITHDRAW_ALL = 4, BTN_TAKE_STORED = 5, BTN_CLEAR_TARGET = 6;
    public static final int INPUT_X = 26, INPUT_Y = 62, INV_Y = 104;

    @Nullable protected final MarketStorageBlockEntity be;
    protected final BlockPos pos;
    protected final ContainerData data;
    private final SimpleContainer input = new SimpleContainer(1);

    protected MachineMenu(MenuType<?> type, int id, Inventory inv, @Nullable MarketStorageBlockEntity be, BlockPos pos,
                          ContainerData data) {
        super(type, id);
        this.be = be;
        this.pos = pos;
        this.data = data;
        addSlot(new Slot(input, 0, INPUT_X, INPUT_Y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return accepts(stack);
            }

            @Override
            public void setChanged() {
                super.setChanged();
                absorb(inv.player);
            }
        });
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
        addDataSlots(data);
    }

    @Nullable
    protected static MarketStorageBlockEntity clientBe(Inventory inv, BlockPos pos) {
        return inv.player.level().getBlockEntity(pos) instanceof MarketStorageBlockEntity m ? m : null;
    }

    protected static BlockPos readPos(RegistryFriendlyByteBuf buf) {
        return buf.readBlockPos();
    }

    protected static ContainerData clientData() {
        return new SimpleContainerData(MarketStorageBlockEntity.DATA_COUNT);
    }

    protected abstract boolean accepts(ItemStack stack);

    /** Server: moves what was put in the input slot into the block. */
    private void absorb(Player player) {
        if (be == null || player.level().isClientSide) return;
        ItemStack s = input.getItem(0);
        if (s.isEmpty()) return;
        ItemStack rest;
        if (Currency.isCurrency(s)) rest = be.depositCurrency(s, false);
        else rest = be instanceof AutoBuyerBlockEntity ? s : be.insertStored(s, false);
        if (rest.getCount() != s.getCount()) input.setItem(0, rest);
    }

    // ------------------------------------------------------------------ synced numbers

    public long stored() {
        return MarketStorageBlockEntity.join(data, 0);
    }

    public long capacity() {
        return MarketStorageBlockEntity.join(data, 4);
    }

    public long balance() {
        return MarketStorageBlockEntity.join(data, 8);
    }

    public long unitPrice() {
        return MarketStorageBlockEntity.join(data, 12);
    }

    public int currencyIndex() {
        return data.get(20);
    }

    public boolean powered() {
        return (data.get(21) & 1) != 0;
    }

    public boolean pending() {
        return (data.get(21) & 2) != 0;
    }

    public int upgrades() {
        return data.get(21) >> 2;
    }

    public int status() {
        return data.get(22);
    }

    public Item selectedCurrency() {
        var ds = Currency.denominations();
        if (ds.isEmpty()) return Items.AIR;
        return ds.get(Math.floorMod(currencyIndex(), ds.size())).item();
    }

    public ItemStack targetStack() {
        if (be == null) return ItemStack.EMPTY;
        Item i = be.targetItem();
        return i == Items.AIR ? ItemStack.EMPTY : new ItemStack(i);
    }

    public String ownerName() {
        return be == null ? "" : be.ownerName();
    }

    public BlockPos pos() {
        return pos;
    }

    // ------------------------------------------------------------------ actions

    /** Server: sets the ghost item. */
    public boolean setTarget(ItemStack stack) {
        return be != null && be.setTarget(stack.copyWithCount(1));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (be == null || !(player instanceof ServerPlayer sp)) return false;
        switch (id) {
            case BTN_CURRENCY_UP -> be.cycleCurrency(-1);
            case BTN_CURRENCY_DOWN -> be.cycleCurrency(1);
            case BTN_WITHDRAW_ONE, BTN_WITHDRAW_STACK -> {
                Item c = be.selectedCurrency();
                if (c == null) return false;
                ItemStack s = be.withdrawCurrency(c, id == BTN_WITHDRAW_ONE ? 1 : 64, false);
                if (s.isEmpty()) return false;
                Give.toPlayer(sp, List.of(s));
            }
            case BTN_WITHDRAW_ALL -> Give.toPlayer(sp, be.withdrawAll());
            case BTN_TAKE_STORED -> {
                ItemStack s = be.takeStored(64);
                if (s.isEmpty()) return false;
                Give.toPlayer(sp, List.of(s));
            }
            case BTN_CLEAR_TARGET -> {
                return be.setTarget(ItemStack.EMPTY);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index == 0) {
            if (!moveItemStackTo(stack, 1, 37, true)) return ItemStack.EMPTY;
        } else {
            if (!accepts(stack) || !moveItemStackTo(stack, 0, 1, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, input);
    }

    @Override
    public boolean stillValid(Player player) {
        return be != null && !be.isRemoved() && be.canAccess(player)
                && player.distanceToSqr(pos.getCenter()) <= 64;
    }
}
