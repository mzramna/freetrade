package dev.freetrade.currency;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.market.ChangeMaker;
import dev.freetrade.util.ItemKeys;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;

/**
 * Money items: the configured list (emerald = 1 U.M. by default) plus Lightman's Currency coins when that mod
 * is installed. Values are milli-U.M. per item. Money is never traded as goods.
 */
public final class Currency {
    public record Denomination(Item item, long milli) {}

    private static volatile List<Denomination> denominations = List.of(
            new Denomination(Items.EMERALD_BLOCK, 9000), new Denomination(Items.EMERALD, 1000));
    private static volatile Map<Item, Long> byItem = Map.of(Items.EMERALD, 1000L, Items.EMERALD_BLOCK, 9000L);

    private Currency() {}

    public static boolean lightmansLoaded() {
        return ModList.get() != null && ModList.get().isLoaded("lightmanscurrency");
    }

    public static void rebuild() {
        Map<Item, Long> map = new LinkedHashMap<>();
        for (var e : FreeTradeConfig.currency.entrySet()) {
            Item i = ItemKeys.item(e.getKey());
            if (i != Items.AIR && e.getValue() > 0) map.put(i, e.getValue());
        }
        if (FreeTradeConfig.lightmansCoins && lightmansLoaded()) {
            try {
                LightmansCompat.coins(FreeTradeConfig.lightmansRate).forEach(map::putIfAbsent);
            } catch (Throwable t) {
                dev.freetrade.FreeTrade.LOG.warn("Lightman's Currency coins unavailable: {}", t.toString());
            }
        }
        List<Denomination> list = new ArrayList<>();
        map.forEach((i, v) -> list.add(new Denomination(i, v)));
        list.sort(Comparator.comparingLong(Denomination::milli).reversed());
        denominations = List.copyOf(list);
        byItem = Map.copyOf(map);
    }

    /** Client: replaces the list with what the server uses. */
    public static void setFromServer(List<Denomination> list) {
        List<Denomination> sorted = new ArrayList<>(list);
        sorted.sort(Comparator.comparingLong(Denomination::milli).reversed());
        Map<Item, Long> map = new LinkedHashMap<>();
        for (Denomination d : sorted) map.put(d.item(), d.milli());
        denominations = List.copyOf(sorted);
        byItem = Map.copyOf(map);
    }

    public static List<Denomination> denominations() {
        return denominations;
    }

    public static boolean isCurrency(Item item) {
        return byItem.containsKey(item);
    }

    public static boolean isCurrency(ItemStack stack) {
        return !stack.isEmpty() && byItem.containsKey(stack.getItem());
    }

    /** Value of ONE item in milli-U.M. (0 when not money). */
    public static long valueOf(Item item) {
        return byItem.getOrDefault(item, 0L);
    }

    public static long valueOf(ItemStack stack) {
        return stack.isEmpty() ? 0 : valueOf(stack.getItem()) * stack.getCount();
    }

    public static Set<String> ids() {
        Set<String> s = new HashSet<>();
        for (Item i : byItem.keySet()) s.add(ItemKeys.id(i));
        return s;
    }

    public record Change(List<ItemStack> stacks, long remainderMilli) {}

    /** Splits an amount into money items, largest first. What cannot be represented is the remainder. */
    public static Change change(long milli) {
        List<ChangeMaker.Denomination<Item>> ds = new ArrayList<>();
        for (Denomination d : denominations) ds.add(new ChangeMaker.Denomination<>(d.item(), d.milli()));
        var r = ChangeMaker.make(milli, ds);
        List<ItemStack> stacks = new ArrayList<>();
        for (var p : r.pieces()) {
            long left = p.count();
            int max = new ItemStack(p.key()).getMaxStackSize();
            while (left > 0) {
                int n = (int) Math.min(max, left);
                stacks.add(new ItemStack(p.key(), n));
                left -= n;
            }
        }
        return new Change(stacks, r.remainder());
    }

    /** Change only in one chosen money item; returns how many fit in the amount. */
    public static long countOf(Item item, long milli) {
        long v = valueOf(item);
        return v <= 0 ? 0 : milli / v;
    }
}
