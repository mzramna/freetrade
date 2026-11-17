package dev.freetrade.menu;

import java.util.ArrayList;
import java.util.List;

import javax.annotation.Nullable;

import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.currency.Currency;
import dev.freetrade.currency.Wallet;
import dev.freetrade.market.Market;
import dev.freetrade.market.Money;
import dev.freetrade.net.FreeTradeNetwork;
import dev.freetrade.registry.ModRegistries;
import dev.freetrade.server.FreeTradeServer;
import dev.freetrade.util.Give;
import dev.freetrade.util.ItemKeys;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * The trade representative's window. Left: a grid that takes ONE kind of item (up to 27 stacks) to sell;
 * only the units that earn whole U.M. are taken, the rest goes back. Right (screen): the catalog to buy from;
 * payment comes from money items in the inventory (or the Lightman's bank) and change is handed back.
 */
public class TraderMenu extends AbstractContainerMenu {
    public static final int GRID_X = 8, GRID_Y = 30, INV_Y = 104;

    @Nullable private final Villager villager;
    private final SimpleContainer grid;
    private boolean busy;

    public TraderMenu(int id, Inventory inv) {
        this(id, inv, null);
    }

    public TraderMenu(int id, Inventory inv, @Nullable Villager villager) {
        super(ModRegistries.TRADER_MENU.get(), id);
        this.villager = villager;
        int size = Math.max(1, Math.min(27, FreeTradeConfig.villagerMaxStacks));
        this.grid = new SimpleContainer(27);
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) {
                int index = c + r * 9;
                addSlot(new Slot(grid, index, GRID_X + c * 18, GRID_Y + r * 18) {
                    @Override
                    public boolean mayPlace(ItemStack stack) {
                        return index < size && accepts(stack);
                    }
                });
            }
        }
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c + r * 9 + 9, 8 + c * 18, INV_Y + r * 18));
        }
        for (int c = 0; c < 9; c++) addSlot(new Slot(inv, c, 8 + c * 18, INV_Y + 58));
    }

    /** One kind of item at a time, never money. */
    public boolean accepts(ItemStack stack) {
        if (stack.isEmpty() || Currency.isCurrency(stack)) return false;
        ItemStack first = gridKind();
        return first.isEmpty() || ItemStack.isSameItem(first, stack);
    }

    public ItemStack gridKind() {
        for (int i = 0; i < grid.getContainerSize(); i++) if (!grid.getItem(i).isEmpty()) return grid.getItem(i);
        return ItemStack.EMPTY;
    }

    public int gridCount() {
        int n = 0;
        for (int i = 0; i < grid.getContainerSize(); i++) n += grid.getItem(i).getCount();
        return n;
    }

    public boolean busy() {
        return busy;
    }

    // ------------------------------------------------------------------ selling

    public void sell(ServerPlayer sp, boolean bank) {
        Market m = FreeTradeServer.market();
        if (busy || m == null) return;
        ItemStack kind = gridKind();
        if (kind.isEmpty()) return;
        String id = ItemKeys.id(kind);
        if (!m.isTradeable(id)) {
            sp.displayClientMessage(Component.translatable("freetrade.trader.not_tradeable", kind.getHoverName()), false);
            return;
        }
        List<ItemStack> escrow = new ArrayList<>();
        double factorSum = 0;
        int total = 0;
        for (int i = 0; i < grid.getContainerSize(); i++) {
            ItemStack s = grid.getItem(i);
            if (s.isEmpty()) continue;
            if (!ItemKeys.isPlain(s, FreeTradeConfig.damagedItems)) {
                sp.displayClientMessage(Component.translatable("freetrade.trader.custom_item"), false);
                continue;
            }
            factorSum += ItemKeys.durabilityFactor(s) * s.getCount();
            total += s.getCount();
            escrow.add(s.copy());
            grid.setItem(i, ItemStack.EMPTY);
        }
        if (total == 0) return;
        double factor = factorSum / total;
        final int offered = total;
        busy = true;
        MinecraftServer server = sp.server;
        m.sell(FreeTradeServer.context(sp, "villager"), id, offered, factor).thenAccept(res -> server.execute(() -> {
            busy = false;
            long back = offered - (res.ok() ? res.units() : 0);
            // give back the unsold units (last stacks first)
            List<ItemStack> returned = new ArrayList<>();
            for (int i = escrow.size() - 1; i >= 0 && back > 0; i--) {
                ItemStack s = escrow.get(i);
                int n = (int) Math.min(back, s.getCount());
                returned.add(s.copyWithCount(n));
                back -= n;
            }
            Give.toPlayer(sp, returned);
            if (!res.ok()) {
                sp.displayClientMessage(errorText(res.error(), kind), false);
                return;
            }
            long payout = res.payoutMilli();
            long left = payout;
            if (bank && Wallet.bankAvailable()) left = Wallet.depositToBank(sp, payout);
            Wallet.giveChange(sp, left);
            sp.displayClientMessage(Component.translatable("freetrade.trader.sold", res.units(), kind.getHoverName(),
                    Money.format(payout)), false);
            FreeTradeNetwork.sendCatalog(sp, false);
        }));
    }

    // ------------------------------------------------------------------ buying

    public void buy(ServerPlayer sp, String id, int quantity, boolean bank) {
        Market m = FreeTradeServer.market();
        if (busy || m == null || quantity <= 0) return;
        int qty = Math.min(quantity, 36 * 64);
        if (!m.isTradeable(id)) return;
        ItemStack kind = ItemKeys.stack(id, 1);
        if (kind.isEmpty()) return;
        busy = true;
        MinecraftServer server = sp.server;
        String player = sp.getUUID().toString();
        m.backoffMultiplier(player, id).thenAccept(mult -> server.execute(() -> {
            long quote = m.quoteBuy(id, qty, mult);
            if (quote < 0) {
                busy = false;
                sp.displayClientMessage(Component.translatable("freetrade.trader.no_stock", kind.getHoverName()), false);
                return;
            }
            boolean useBank = bank && Wallet.bankAvailable();
            long taken = useBank ? Wallet.takeFromBank(sp, quote) : Wallet.takeFromInventory(sp.getInventory(), quote);
            if (taken <= 0) {
                busy = false;
                sp.displayClientMessage(Component.translatable("freetrade.trader.no_money", Money.format(quote)), false);
                return;
            }
            m.buy(FreeTradeServer.context(sp, "villager"), id, qty, taken, true).thenAccept(res -> server.execute(() -> {
                busy = false;
                long change = res.ok() ? taken - res.costMilli() : taken;
                if (useBank) change = Wallet.depositToBank(sp, change);
                Wallet.giveChange(sp, change);
                if (!res.ok()) {
                    sp.displayClientMessage(errorText(res.error(), kind), false);
                    return;
                }
                List<ItemStack> items = new ArrayList<>();
                long left = res.units();
                int max = kind.getMaxStackSize();
                while (left > 0) {
                    int n = (int) Math.min(max, left);
                    items.add(kind.copyWithCount(n));
                    left -= n;
                }
                Give.toPlayer(sp, items);
                sp.displayClientMessage(Component.translatable("freetrade.trader.bought", res.units(), kind.getHoverName(),
                        Money.format(res.costMilli())), false);
                FreeTradeNetwork.sendCatalog(sp, false);
            }));
        }));
    }

    private static Component errorText(Market.Error e, ItemStack kind) {
        return switch (e) {
            case TOO_FEW -> Component.translatable("freetrade.trader.too_few", kind.getHoverName());
            case NO_STOCK -> Component.translatable("freetrade.trader.no_stock", kind.getHoverName());
            case NO_FUNDS -> Component.translatable("freetrade.trader.price_changed");
            case TREASURY_EMPTY -> Component.translatable("freetrade.trader.treasury_empty");
            case NOT_TRADEABLE -> Component.translatable("freetrade.trader.not_tradeable", kind.getHoverName());
            default -> Component.translatable("freetrade.error.database");
        };
    }

    // ------------------------------------------------------------------ container

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        if (index < 27) {
            if (!moveItemStackTo(stack, 27, 63, true)) return ItemStack.EMPTY;
        } else {
            if (!accepts(stack)) return ItemStack.EMPTY;
            int size = Math.max(1, Math.min(27, FreeTradeConfig.villagerMaxStacks));
            if (!moveItemStackTo(stack, 0, size, false)) return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) slot.setByPlayer(ItemStack.EMPTY);
        else slot.setChanged();
        return copy;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide) clearContainer(player, grid);
        if (villager != null) villager.setTradingPlayer(null);
    }

    @Override
    public boolean stillValid(Player player) {
        if (villager == null) return true;
        return villager.isAlive() && player.distanceToSqr(villager) <= 64
                && villager.getVillagerData().getProfession() == ModRegistries.TRADE_REPRESENTATIVE.get();
    }
}
