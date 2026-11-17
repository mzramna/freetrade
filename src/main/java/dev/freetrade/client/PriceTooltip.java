package dev.freetrade.client;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import dev.freetrade.currency.Currency;
import dev.freetrade.net.Catalog;
import dev.freetrade.net.ClientHooks;
import dev.freetrade.net.FreeTradeNetwork;
import dev.freetrade.util.ItemKeys;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Appends the association's price (and money value) to item tooltips. Details are shown while Shift is held. */
public final class PriceTooltip {
    private static FreeTradeNetwork.CatalogPayload indexed;
    private static Map<String, Catalog.Entry> index = Map.of();

    private PriceTooltip() {}

    public static void onTooltip(ItemTooltipEvent event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return;
        List<Component> lines = event.getToolTip();

        FreeTradeNetwork.CatalogPayload cat = ClientHooks.lastCatalog;
        if (cat != null) {
            Catalog.Entry e = entryFor(cat, ItemKeys.id(stack));
            if (e != null) {
                lines.add(Component.translatable("freetrade.tooltip.price", CatalogWidget.price(e.sell()),
                        CatalogWidget.price(e.buy()), e.stock()).withStyle(ChatFormatting.GRAY));
                if (e.lot() > 1) {
                    lines.add(Component.translatable("freetrade.tooltip.lot", e.lot()).withStyle(ChatFormatting.GRAY));
                }
                if (Minecraft.getInstance().hasShiftDown()) {
                    lines.add(Component.translatable(e.declared() ? "freetrade.tooltip.declared"
                            : "freetrade.tooltip.derived").withStyle(ChatFormatting.DARK_GRAY));
                }
            }
        }
        if (Currency.isCurrency(stack)) {
            lines.add(Component.translatable("freetrade.tooltip.money",
                    dev.freetrade.market.Money.format(Currency.valueOf(stack.getItem()))).withStyle(ChatFormatting.GRAY));
        }
    }

    private static Catalog.Entry entryFor(FreeTradeNetwork.CatalogPayload cat, String id) {
        if (cat != indexed) {
            Map<String, Catalog.Entry> m = new HashMap<>();
            for (Catalog.Entry e : cat.entries()) m.put(e.item(), e);
            index = m;
            indexed = cat;
        }
        return index.get(id);
    }
}
