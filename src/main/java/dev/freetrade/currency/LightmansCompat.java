package dev.freetrade.currency;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.lightman314.lightmanscurrency.api.LCApi;
import io.github.lightman314.lightmanscurrency.api.bank_account.BankAccount;
import io.github.lightman314.lightmanscurrency.api.bank_account.reference.BankReference;
import io.github.lightman314.lightmanscurrency.api.bank_account.reference.builtin.PlayerBankReference;
import io.github.lightman314.lightmanscurrency.api.coins.CoinAPI;
import io.github.lightman314.lightmanscurrency.api.coins.data.ChainData;
import io.github.lightman314.lightmanscurrency.api.coins.data.coin.CoinEntry;
import io.github.lightman314.lightmanscurrency.api.coins.value.CoinValue;
import io.github.lightman314.lightmanscurrency.api.money.values.MoneyValue;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.transfer.transaction.Transaction;

/**
 * Lightman's Currency bridge (26.1 API). Only touched when the mod is loaded ({@link Currency#lightmansLoaded()}).
 * One U.M. = 1 / rate units of the default coin chain's internal value.
 */
public final class LightmansCompat {
    private LightmansCompat() {}

    /** Default-chain coins with their value in milli-U.M. */
    static Map<Item, Long> coins(double umPerCore) {
        Map<Item, Long> out = new LinkedHashMap<>();
        ChainData chain = LCApi.getCoinAPI().lookupChain(CoinAPI.DEFAULT_CHAIN);
        if (chain == null) return out;
        for (CoinEntry e : chain.getAllEntries(false)) {
            long milli = Math.round(e.getInternalValue() * umPerCore * 1000.0);
            if (milli > 0) out.put(e.getCoin(), milli);
        }
        return out;
    }

    private static BankAccount account(Player player) {
        BankReference ref = PlayerBankReference.of(player);
        return ref == null ? null : ref.get();
    }

    private static long toMilli(long core, double umPerCore) {
        return (long) Math.floor(core * umPerCore * 1000.0);
    }

    /** Bank balance of the player's personal account in milli-U.M. (-1 when there is no account). */
    public static long bankBalance(Player player, double umPerCore) {
        BankAccount acc = account(player);
        if (acc == null) return -1;
        MoneyValue key = CoinValue.fromNumber(CoinAPI.DEFAULT_CHAIN, 1);
        MoneyValue v = acc.getMoneyStorage().getResource(key.getKey());
        return v == null || v.isEmpty() ? 0 : toMilli(v.getInternalValue(), umPerCore);
    }

    /** Withdraws at least {@code milli} (rounded up to whole core units). Returns the milli actually taken or -1. */
    public static long withdraw(Player player, long milli, double umPerCore) {
        BankAccount acc = account(player);
        if (acc == null) return -1;
        long core = (long) Math.ceil(milli / 1000.0 / umPerCore);
        MoneyValue want = CoinValue.fromNumber(CoinAPI.DEFAULT_CHAIN, core);
        try (Transaction tx = Transaction.openRoot()) {
            MoneyValue got = acc.withdrawMoney(want, tx);
            if (got == null || got.isEmpty() || got.getInternalValue() < core) return -1;
            tx.commit();
            return toMilli(got.getInternalValue(), umPerCore);
        }
    }

    /** Deposits {@code milli} (rounded down to whole core units). Returns the milli deposited (0 if none). */
    public static long deposit(Player player, long milli, double umPerCore) {
        BankAccount acc = account(player);
        if (acc == null) return 0;
        long core = (long) Math.floor(milli / 1000.0 / umPerCore);
        if (core <= 0) return 0;
        try (Transaction tx = Transaction.openRoot()) {
            acc.depositMoney(CoinValue.fromNumber(CoinAPI.DEFAULT_CHAIN, core), tx);
            tx.commit();
        }
        return toMilli(core, umPerCore);
    }
}
