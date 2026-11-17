package dev.freetrade.util;

import net.minecraft.core.component.DataComponentPatch;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Item id strings and the rules for which stacks the association accepts. */
public final class ItemKeys {
    private ItemKeys() {}

    public static String id(Item item) {
        return BuiltInRegistries.ITEM.getKey(item).toString();
    }

    public static String id(ItemStack stack) {
        return id(stack.getItem());
    }

    public static Item item(String id) {
        ResourceLocation rl = ResourceLocation.tryParse(id);
        if (rl == null) return Items.AIR;
        return BuiltInRegistries.ITEM.getOptional(rl).orElse(Items.AIR);
    }

    public static ItemStack stack(String id, int count) {
        Item i = item(id);
        return i == Items.AIR ? ItemStack.EMPTY : new ItemStack(i, count);
    }

    /**
     * True when the stack carries nothing but (optionally) damage: enchantments, names, contents and other
     * custom data make an item a different product, so those stacks are refused.
     */
    public static boolean isPlain(ItemStack stack, boolean allowDamage) {
        DataComponentPatch patch = stack.getComponentsPatch();
        if (patch.isEmpty()) return true;
        for (var e : patch.entrySet()) {
            var type = e.getKey();
            if (allowDamage && type == DataComponents.DAMAGE) continue;
            if (type == DataComponents.REPAIR_COST && e.getValue().isPresent() && Integer.valueOf(0).equals(e.getValue().get())) {
                continue;
            }
            return false;
        }
        return true;
    }

    /** Fraction of the value paid for a damaged item (1 for undamaged / non-damageable). */
    public static double durabilityFactor(ItemStack stack) {
        if (!stack.isDamageableItem() || !stack.isDamaged()) return 1.0;
        int max = stack.getMaxDamage();
        if (max <= 0) return 1.0;
        return Math.max(0, (max - stack.getDamageValue()) / (double) max);
    }
}
