package dev.freetrade.menu;

import dev.freetrade.block.AutoBuyerBlockEntity;
import dev.freetrade.currency.Currency;
import dev.freetrade.registry.ModRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class AutoBuyerMenu extends MachineMenu {
    public AutoBuyerMenu(int id, Inventory inv, AutoBuyerBlockEntity be) {
        super(ModRegistries.AUTO_BUYER_MENU.get(), id, inv, be, be.getBlockPos(), be.data);
    }

    public AutoBuyerMenu(int id, Inventory inv, RegistryFriendlyByteBuf buf) {
        this(id, inv, readPos(buf));
    }

    private AutoBuyerMenu(int id, Inventory inv, net.minecraft.core.BlockPos pos) {
        super(ModRegistries.AUTO_BUYER_MENU.get(), id, inv, clientBe(inv, pos), pos, clientData());
    }

    @Override
    protected boolean accepts(ItemStack stack) {
        return Currency.isCurrency(stack);
    }
}
