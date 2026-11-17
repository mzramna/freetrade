package dev.freetrade.currency;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.lightman314.lightmanscurrency.api.money.bank.BankAPI;
import io.github.lightman314.lightmanscurrency.api.money.bank.IBankAccount;
import io.github.lightman314.lightmanscurrency.api.money.bank.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.money.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.money.coins.data.ChainData;
import io.github.lightman314.lightmanscurrency.api.money.coins.data.coin.CoinEntry;
import io.github.lightman314.lightmanscurrency.api.money.value.MoneyValue;
import io.github.lightman314.lightmanscurrency.api.money.value.builtin.CoinValue;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;

/**
 * Lightman's Currency bridge. Only touched when the mod is loaded ({@link Currency#lightmansLoaded()}).
 * One U.M. = 1 / rate core value units of the main coin chain.
 */
public final class LightmansCompat {
    private LightmansCompat() {}

    /** Main-chain coins with their value in milli-U.M. */
    static Map<Item, Long> coins(double umPerCore) {
        Map<Item, Long> out = new LinkedHashMap<>();
        ChainData chain = CoinAPI.getApi().ChainData(CoinAPI.MAIN_CHAIN);
        if (chain == null) return out;
        for (CoinEntry e : chain.getAllEntries(false)) {
            long milli = Math.round(e.getCoreValue() * umPerCore * 1000.0);
            if (milli > 0) out.put(e.getCoin(), milli);
        }
        return out;
    }

    private static IBankAccount account(Player player) {
        var ref = PlayerBankReference.of(player);
        return ref == null ? null : ref.get();
    }

    private static long toCore(long milli, double umPerCore) {
        return (long) Math.floor(milli / 1000.0 / umPerCore);
    }

    private static long toMilli(long core, double umPerCore) {
        return (long) Math.floor(core * umPerCore * 1000.0);
    }

    /** Bank balance of the player's personal account in milli-U.M. (-1 when there is no account). */
    public static long bankBalance(Player player, double umPerCore) {
        IBankAccount acc = account(player);
        if (acc == null) return -1;
        String key = CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, 1).getUniqueName();
        MoneyValue v = acc.getMoneyStorage().valueOf(key);
        return v == null ? 0 : toMilli(v.getCoreValue(), umPerCore);
    }

    /** Withdraws at least {@code milli} (rounded up to whole core units). Returns the milli actually taken or -1. */
    public static long withdraw(Player player, long milli, double umPerCore) {
        IBankAccount acc = account(player);
        if (acc == null) return -1;
        long core = (long) Math.ceil(milli / 1000.0 / umPerCore);
        MoneyValue want = CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, core);
        var res = BankAPI.getApi().BankWithdrawFromServer(acc, want);
        if (res == null || !Boolean.TRUE.equals(res.getFirst())) return -1;
        return toMilli(res.getSecond().getCoreValue(), umPerCore);
    }

    /** Deposits {@code milli} (rounded down to whole core units). Returns the milli deposited (0 if none). */
    public static long deposit(Player player, long milli, double umPerCore) {
        IBankAccount acc = account(player);
        if (acc == null) return 0;
        long core = toCore(milli, umPerCore);
        if (core <= 0) return 0;
        boolean ok = BankAPI.getApi().BankDepositFromServer(acc, CoinValue.fromNumber(CoinAPI.MAIN_CHAIN, core));
        return ok ? toMilli(core, umPerCore) : 0;
    }
}
