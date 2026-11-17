package dev.freetrade.menu;

import dev.freetrade.block.SellPointBlockEntity;
import dev.freetrade.currency.Currency;
import dev.freetrade.registry.ModRegistries;
import dev.freetrade.util.ItemKeys;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class SellPointMenu extends MachineMenu {
    public SellPointMenu(int id, Inventory inv, SellPointBlockEntity be) {
        super(ModRegistries.SELL_POINT_MENU.get(), id, inv, be, be.getBlockPos(), be.data);
    }

    public SellPointMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, readPos(buf));
    }

    private SellPointMenu(int id, Inventory inv, net.minecraft.core.BlockPos pos) {
        super(ModRegistries.SELL_POINT_MENU.get(), id, inv, clientBe(inv, pos), pos, clientData());
    }

    @Override
    protected boolean accepts(ItemStack stack) {
        if (Currency.isCurrency(stack)) return true;
        return resolve() != null && !be.target().isEmpty() && ItemKeys.id(stack).equals(be.target()) && ItemKeys.isPlain(stack, false);
    }
}
