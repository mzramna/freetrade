package dev.freetrade.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.ArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;

import dev.freetrade.FreeTrade;
import dev.freetrade.config.ValueTable;
import dev.freetrade.currency.Currency;
import dev.freetrade.db.MarketStore;
import dev.freetrade.market.Market;
import dev.freetrade.market.Money;
import dev.freetrade.server.FreeTradeServer;
import dev.freetrade.server.Triggers;
import dev.freetrade.util.Give;
import dev.freetrade.util.ItemKeys;
import dev.freetrade.valuation.Valuation;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.item.ItemArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * /freetrade (alias /ft). Players: price, help. OPs (level 2): recalc, reload, setstock, setvalue, unpriced,
 * history, account (treasury / tax: query, balance, deposit, withdraw, give) and trigger.
 */
public final class FreeTradeCommand {
    private static final SuggestionProvider<CommandSourceStack> ACCOUNTS =
            (c, b) -> SharedSuggestionProvider.suggest(List.of(MarketStore.TREASURY, MarketStore.TAX), b);
    private static final SuggestionProvider<CommandSourceStack> MODES =
            (c, b) -> SharedSuggestionProvider.suggest(List.of("once", "repeat"), b);
    private static final SuggestionProvider<CommandSourceStack> TRIGGER_IDS =
            (c, b) -> SharedSuggestionProvider.suggest(Triggers.list().stream().map(MarketStore.Trigger::id), b);

    private FreeTradeCommand() {}

    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        CommandBuildContext ctx = event.getBuildContext();
        var root = Commands.literal("freetrade")
                .then(Commands.literal("price")
                        .executes(c -> price(c, c.getSource().getPlayerOrException().getMainHandItem().getItem()))
                        .then(Commands.argument("item", ItemArgument.item(ctx))
                                .executes(c -> price(c, ItemArgument.getItem(c, "item").getItem()))))
                .then(Commands.literal("recalc").requires(s -> s.hasPermission(2)).executes(FreeTradeCommand::recalc))
                .then(Commands.literal("reload").requires(s -> s.hasPermission(2)).executes(FreeTradeCommand::reload))
                .then(Commands.literal("setstock").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("item", ItemArgument.item(ctx))
                                .then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(FreeTradeCommand::setStock))))
                .then(Commands.literal("stock").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("item", ItemArgument.item(ctx))
                                .executes(FreeTradeCommand::showStock)
                                .then(Commands.literal("set").then(Commands.argument("amount", LongArgumentType.longArg(0))
                                        .executes(c -> changeStock(c, 0))))
                                .then(Commands.literal("add").then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(c -> changeStock(c, 1))))
                                .then(Commands.literal("remove").then(Commands.argument("amount", LongArgumentType.longArg(1))
                                        .executes(c -> changeStock(c, -1))))))
                .then(Commands.literal("tax").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("list").executes(FreeTradeCommand::taxList))
                        .then(Commands.argument("item", ItemArgument.item(ctx))
                                .executes(FreeTradeCommand::taxShow)
                                .then(Commands.literal("sell").then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(c -> taxSet(c, "sell"))))
                                .then(Commands.literal("buy").then(Commands.argument("value", StringArgumentType.greedyString())
                                        .executes(c -> taxSet(c, "buy"))))
                                .then(Commands.literal("clear")
                                        .executes(c -> taxClear(c, null))
                                        .then(Commands.literal("sell").executes(c -> taxClear(c, "sell")))
                                        .then(Commands.literal("buy").executes(c -> taxClear(c, "buy"))))))
                .then(Commands.literal("forceprice").requires(s -> s.hasPermission(2))
                        .then(Commands.literal("list").executes(FreeTradeCommand::forcedList))
                        .then(Commands.argument("item", ItemArgument.item(ctx))
                                .executes(FreeTradeCommand::showForced)
                                .then(Commands.literal("set").then(Commands.argument("value", DoubleArgumentType.doubleArg(0.001))
                                        .executes(c -> forcePrice(c, 0))))
                                .then(Commands.literal("add").then(Commands.argument("value", DoubleArgumentType.doubleArg(0.001))
                                        .executes(c -> forcePrice(c, 1))))
                                .then(Commands.literal("remove").then(Commands.argument("value", DoubleArgumentType.doubleArg(0.001))
                                        .executes(c -> forcePrice(c, -1))))
                                .then(Commands.literal("clear").executes(FreeTradeCommand::clearForced))))
                .then(Commands.literal("setvalue").requires(s -> s.hasPermission(2))
                        .then(Commands.argument("item", ItemArgument.item(ctx))
                                .then(Commands.argument("value", DoubleArgumentType.doubleArg(0))
                                        .executes(FreeTradeCommand::setValue))))
                .then(Commands.literal("unpriced").requires(s -> s.hasPermission(2))
                        .executes(c -> unpriced(c, 1))
                        .then(Commands.argument("page", IntegerArgumentType.integer(1))
                                .executes(c -> unpriced(c, IntegerArgumentType.getInteger(c, "page")))))
                .then(Commands.literal("history").requires(s -> s.hasPermission(2))
                        .executes(c -> history(c, null))
                        .then(Commands.argument("item", ItemArgument.item(ctx))
                                .executes(c -> history(c, ItemKeys.id(ItemArgument.getItem(c, "item").getItem())))))
                .then(account(ctx, "account", null))
                .then(account(ctx, "treasury", MarketStore.TREASURY))
                .then(account(ctx, "taxaccount", MarketStore.TAX))
                .then(trigger());
        var node = d.register(root);
        d.register(Commands.literal("ft").redirect(node));
    }

    private static Market market(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Market m = FreeTradeServer.market();
        if (m == null) {
            throw new com.mojang.brigadier.exceptions.SimpleCommandExceptionType(
                    Component.translatable("freetrade.error.offline")).create();
        }
        return m;
    }

    private static void reply(CommandSourceStack s, Component msg) {
        s.sendSuccess(() -> msg, false);
    }

    /** Sends the outcome of async work back on the server thread. */
    private static <T> void later(CommandSourceStack s, CompletableFuture<T> f, java.util.function.Function<T, Component> ok) {
        MinecraftServer server = s.getServer();
        f.whenComplete((v, t) -> server.execute(() -> {
            if (t != null) {
                FreeTrade.LOG.error("Free Trade command failed", t);
                s.sendFailure(Component.translatable("freetrade.error.database"));
            } else {
                Component c = ok.apply(v);
                if (c != null) reply(s, c);
            }
        }));
    }

    // ------------------------------------------------------------------ price

    private static int price(CommandContext<CommandSourceStack> c, Item item) throws CommandSyntaxException {
        Market m = market(c);
        String id = ItemKeys.id(item);
        CommandSourceStack s = c.getSource();
        if (Currency.isCurrency(item)) {
            reply(s, Component.translatable("freetrade.price.currency", item.getDescription(), Money.format(Currency.valueOf(item))));
            return 1;
        }
        if (!m.isTradeable(id)) {
            s.sendFailure(Component.translatable("freetrade.price.none", item.getDescription()));
            return 0;
        }
        long stock = m.stock(id);
        long lot = m.lotSize(id);
        long sell = m.quoteSell(id, lot, 1.0);
        long buy = m.quoteBuy(id, lot, 1.0);
        reply(s, Component.translatable("freetrade.price.header", item.getDescription(), stock));
        reply(s, Component.translatable("freetrade.price.sell", Money.format(sell)));
        reply(s, buy < 0 ? Component.translatable("freetrade.price.buy_none")
                : Component.translatable("freetrade.price.buy", Money.format(buy)));
        if (lot > 1) reply(s, Component.translatable("freetrade.price.lot", lot));
        reply(s, Component.translatable("freetrade.price.base", String.format(Locale.ROOT, "%.3f", m.baseValue(id)),
                m.reference(id), m.snapshot().declared().containsKey(id)
                        ? Component.translatable("freetrade.price.declared")
                        : Component.translatable("freetrade.price.derived")));
        return (int) Math.min(Integer.MAX_VALUE, Money.floorUnits(Math.max(0, buy)));
    }

    // ------------------------------------------------------------------ admin

    private static int recalc(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Market m = market(c);
        reply(c.getSource(), Component.translatable("freetrade.recalc.start"));
        later(c.getSource(), Valuation.recalc(c.getSource().getServer(), m),
                o -> Component.translatable("freetrade.recalc.done", o.derived(), o.unpriced(), o.millis()));
        return 1;
    }

    private static int reload(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Market m = market(c);
        FreeTradeServer.applyConfig();
        var in = Valuation.gather(c.getSource().getServer());
        later(c.getSource(), CompletableFuture.supplyAsync(() -> {
            try {
                return Valuation.apply(m, in, false);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }), o -> Component.translatable("freetrade.reload.done"));
        return 1;
    }

    private static int setStock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Market m = market(c);
        Item item = ItemArgument.getItem(c, "item").getItem();
        long amount = LongArgumentType.getLong(c, "amount");
        later(c.getSource(), m.setStock(ItemKeys.id(item), amount),
                v -> Component.translatable("freetrade.setstock.done", item.getDescription(), v));
        return 1;
    }

    private static Item itemArg(CommandContext<CommandSourceStack> c) {
        return ItemArgument.getItem(c, "item").getItem();
    }

    private static int showStock(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Market m = market(c);
        Item item = itemArg(c);
        long v = m.stock(ItemKeys.id(item));
        reply(c.getSource(), Component.translatable("freetrade.stock.show", item.getDescription(), v));
        return (int) Math.min(Integer.MAX_VALUE, v);
    }

    /** mode 0 = set, 1 = add, -1 = remove. */
    private static int changeStock(CommandContext<CommandSourceStack> c, int mode) throws CommandSyntaxException {
        Market m = market(c);
        Item item = itemArg(c);
        if (Currency.isCurrency(item)) {
            c.getSource().sendFailure(Component.translatable("freetrade.price.currency", item.getDescription(),
                    Money.format(Currency.valueOf(item))));
            return 0;
        }
        long amount = LongArgumentType.getLong(c, "amount");
        String id = ItemKeys.id(item);
        CompletableFuture<Long> f = mode == 0 ? m.setStock(id, amount) : m.adjustStock(id, mode * amount);
        later(c.getSource(), f, v -> Component.translatable("freetrade.setstock.done", item.getDescription(), v));
        return 1;
    }

    // ------------------------------------------------------------------ per-item taxes

    private static Component taxText(dev.freetrade.market.TaxRule r, double globalPercent) {
        if (r == null) return Component.translatable("freetrade.tax.global", String.format(Locale.ROOT, "%s%%",
                globalPercent == Math.rint(globalPercent) ? Long.toString((long) globalPercent) : Double.toString(globalPercent)));
        return r.percent() ? Component.literal(r.toString()) : Component.translatable("freetrade.tax.fixed", r.toString());
    }

    private static int taxShow(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Market m = market(c);
        Item item = itemArg(c);
        String id = ItemKeys.id(item);
        reply(c.getSource(), Component.translatable("freetrade.tax.show", item.getDescription(),
                taxText(m.sellTaxRule(id), m.settings().taxSellPercent()),
                taxText(m.buyTaxRule(id), m.settings().taxBuyPercent())));
        return 1;
    }

    private static int taxSet(CommandContext<CommandSourceStack> c, String direction) throws CommandSyntaxException {
        Market m = market(c);
        Item item = itemArg(c);
        String raw = StringArgumentType.getString(c, "value").trim();
        dev.freetrade.market.TaxRule rule;
        try {
            rule = dev.freetrade.market.TaxRule.parse(raw);
        } catch (IllegalArgumentException e) {
            c.getSource().sendFailure(Component.translatable("freetrade.tax.invalid", raw));
            return 0;
        }
        try {
            ValueTable.putTax(ItemKeys.id(item), direction, rule.toString());
        } catch (Exception e) {
            c.getSource().sendFailure(Component.literal(e.toString()));
            return 0;
        }
        Valuation.applyTaxes(m, ValueTable.get());
        return taxShow(c);
    }

    private static int taxClear(CommandContext<CommandSourceStack> c, String direction) throws CommandSyntaxException {
        Market m = market(c);
        Item item = itemArg(c);
        try {
            if (direction == null || direction.equals("sell")) ValueTable.putTax(ItemKeys.id(item), "sell", null);
            if (direction == null || direction.equals("buy")) ValueTable.putTax(ItemKeys.id(item), "buy", null);
        } catch (Exception e) {
            c.getSource().sendFailure(Component.literal(e.toString()));
            return 0;
        }
        Valuation.applyTaxes(m, ValueTable.get());
        return taxShow(c);
    }

    private static int taxList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        market(c);
        ValueTable.Data t = ValueTable.get();
        var keys = new java.util.TreeSet<String>();
        keys.addAll(t.sellTaxes().keySet());
        keys.addAll(t.buyTaxes().keySet());
        if (keys.isEmpty()) reply(c.getSource(), Component.translatable("freetrade.tax.list_empty"));
        for (String k : keys) {
            reply(c.getSource(), Component.literal(" - " + k + "  sell=" + t.sellTaxes().getOrDefault(k, "-")
                    + "  buy=" + t.buyTaxes().getOrDefault(k, "-")));
        }
        return keys.size();
    }

    private static int showForced(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Market m = market(c);
        Item item = itemArg(c);
        Double v = m.forcedPrice(ItemKeys.id(item));
        reply(c.getSource(), v == null
                ? Component.translatable("freetrade.forceprice.none", item.getDescription())
                : Component.translatable("freetrade.forceprice.show", item.getDescription(), Money.format(Money.ofDouble(v))));
        return v == null ? 0 : 1;
    }

    /** mode 0 = set, 1 = add, -1 = remove (relative to the forced price, or to the current market price). */
    private static int forcePrice(CommandContext<CommandSourceStack> c, int mode) throws CommandSyntaxException {
        Market m = market(c);
        Item item = itemArg(c);
        if (Currency.isCurrency(item)) {
            c.getSource().sendFailure(Component.translatable("freetrade.price.currency", item.getDescription(),
                    Money.format(Currency.valueOf(item))));
            return 0;
        }
        String id = ItemKeys.id(item);
        double value = DoubleArgumentType.getDouble(c, "value");
        Double forced = m.forcedPrice(id);
        double current = forced != null ? forced : (m.isTradeable(id) ? m.unitPrice(id) : 0);
        double next = mode == 0 ? value : current + mode * value;
        if (next <= 0) {
            c.getSource().sendFailure(Component.translatable("freetrade.forceprice.not_positive"));
            return 0;
        }
        later(c.getSource(), m.forcePrice(id, next), v -> Component.translatable("freetrade.forceprice.show",
                item.getDescription(), Money.format(Money.ofDouble(v))));
        return 1;
    }

    private static int clearForced(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Market m = market(c);
        Item item = itemArg(c);
        later(c.getSource(), m.forcePrice(ItemKeys.id(item), 0),
                v -> Component.translatable("freetrade.forceprice.cleared", item.getDescription()));
        return 1;
    }

    private static int forcedList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        Market m = market(c);
        var all = new java.util.TreeMap<>(m.forcedPrices());
        if (all.isEmpty()) reply(c.getSource(), Component.translatable("freetrade.forceprice.list_empty"));
        all.forEach((id, v) -> reply(c.getSource(), Component.literal(" - " + id + " = " + Money.format(Money.ofDouble(v)) + " U.M.")));
        return all.size();
    }

    private static int setValue(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        market(c);
        Item item = ItemArgument.getItem(c, "item").getItem();
        double value = DoubleArgumentType.getDouble(c, "value");
        try {
            ValueTable.putValue(ItemKeys.id(item), value);
        } catch (Exception e) {
            c.getSource().sendFailure(Component.literal(e.toString()));
            return 0;
        }
        reply(c.getSource(), Component.translatable("freetrade.setvalue.done", item.getDescription(), value));
        return reload(c);
    }

    private static int unpriced(CommandContext<CommandSourceStack> c, int page) throws CommandSyntaxException {
        market(c);
        List<String> list = new ArrayList<>(Valuation.lastUnpriced());
        list.sort(String::compareTo);
        int per = 15, pages = Math.max(1, (list.size() + per - 1) / per);
        int p = Math.min(page, pages);
        reply(c.getSource(), Component.translatable("freetrade.unpriced.header", list.size(), p, pages));
        for (int i = (p - 1) * per; i < Math.min(list.size(), p * per); i++) {
            reply(c.getSource(), Component.literal(" - " + list.get(i)));
        }
        return list.size();
    }

    private static int history(CommandContext<CommandSourceStack> c, String item) throws CommandSyntaxException {
        Market m = market(c);
        later(c.getSource(), m.database().submit(conn -> m.store().recentTx(conn, item, 10)), txs -> {
            CommandSourceStack s = c.getSource();
            if (txs.isEmpty()) return Component.translatable("freetrade.history.empty");
            for (MarketStore.Tx t : txs) {
                String who = t.playerName() == null ? "-" : t.playerName();
                reply(s, Component.literal(String.format(Locale.ROOT, "[%s] %s %s %+d %s  %s U.M. (tax %s) stock=%d",
                        t.serverId(), new java.text.SimpleDateFormat("MM-dd HH:mm").format(new java.util.Date(t.ts())),
                        who, t.qty(), t.item(), Money.format(t.amount()), Money.format(t.tax()), t.stockAfter())));
            }
            return null;
        });
        return 1;
    }

    // ------------------------------------------------------------------ accounts

    private static String accountName(CommandContext<CommandSourceStack> c, String fixed) {
        return fixed != null ? fixed : StringArgumentType.getString(c, "account");
    }

    private static ArgumentBuilder<CommandSourceStack, ?> account(CommandBuildContext ctx, String literal, String fixed) {
        var lit = Commands.literal(literal).requires(s -> s.hasPermission(2));
        ArgumentBuilder<CommandSourceStack, ?> base = fixed == null
                ? Commands.argument("account", StringArgumentType.word()).suggests(ACCOUNTS)
                : lit;
        base.then(Commands.literal("balance").executes(c -> balance(c, accountName(c, fixed))))
                .then(queryNode(fixed))
                .then(Commands.literal("deposit")
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.001))
                                .executes(c -> adjust(c, accountName(c, fixed), true))))
                .then(Commands.literal("withdraw")
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.001))
                                .executes(c -> withdraw(c, accountName(c, fixed), null, null))
                                .then(Commands.argument("item", ItemArgument.item(ctx))
                                        .executes(c -> withdraw(c, accountName(c, fixed), null,
                                                ItemArgument.getItem(c, "item").getItem())))))
                .then(Commands.literal("remove")
                        .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.001))
                                .executes(c -> adjust(c, accountName(c, fixed), false))))
                .then(Commands.literal("give")
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("amount", DoubleArgumentType.doubleArg(0.001))
                                        .executes(c -> withdraw(c, accountName(c, fixed),
                                                EntityArgument.getPlayer(c, "player"), null)))));
        return fixed == null ? lit.then(base) : base;
    }

    private static int balance(CommandContext<CommandSourceStack> c, String acc) throws CommandSyntaxException {
        Market m = market(c);
        long bal = m.account(acc);
        boolean infinite = MarketStore.TREASURY.equals(acc) && m.treasuryInfinite();
        reply(c.getSource(), infinite
                ? Component.translatable("freetrade.account.balance_infinite", acc, Money.format(bal))
                : Component.translatable("freetrade.account.balance", acc, Money.format(bal)));
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, Money.floorUnits(bal)));
    }

    private static int query(CommandContext<CommandSourceStack> c, String acc, String op) throws CommandSyntaxException {
        Market m = market(c);
        long value = Money.ofDouble(Math.abs(DoubleArgumentType.getDouble(c, "value")))
                * (DoubleArgumentType.getDouble(c, "value") < 0 ? -1 : 1);
        var t = new MarketStore.Trigger("", acc, op, value, 0, "once", 0, "", 0, 0, true, true);
        boolean result = t.test(m.account(acc));
        if (!result) {
            c.getSource().sendFailure(Component.translatable("freetrade.account.query_false", acc, op, Money.format(value)));
            return 0;
        }
        reply(c.getSource(), Component.translatable("freetrade.account.query_true", acc, op, Money.format(value)));
        return 1;
    }

    private static int adjust(CommandContext<CommandSourceStack> c, String acc, boolean deposit) throws CommandSyntaxException {
        Market m = market(c);
        long amount = Money.ofDouble(DoubleArgumentType.getDouble(c, "amount"));
        Market.TradeContext tc = actor(c.getSource());
        later(c.getSource(), m.adjustAccount(tc, acc, deposit ? amount : -amount, !deposit), v -> v == null
                ? Component.translatable("freetrade.account.insufficient", acc)
                : Component.translatable("freetrade.account.balance", acc, Money.format(v)));
        return 1;
    }

    private static Market.TradeContext actor(CommandSourceStack s) {
        ServerPlayer p = s.getPlayer();
        return p != null ? FreeTradeServer.context(p, "command") : new Market.TradeContext(null, s.getTextName(), "command");
    }

    /** Takes money from the account and gives it as money items (to the target or to the executing player). */
    private static int withdraw(CommandContext<CommandSourceStack> c, String acc, ServerPlayer target, Item only)
            throws CommandSyntaxException {
        Market m = market(c);
        ServerPlayer to = target != null ? target : c.getSource().getPlayer();
        if (to == null) {
            c.getSource().sendFailure(Component.translatable("freetrade.account.no_player"));
            return 0;
        }
        long requested = Money.ofDouble(DoubleArgumentType.getDouble(c, "amount"));
        long amount = requested;
        if (only != null) {
            long v = Currency.valueOf(only);
            if (v <= 0) {
                c.getSource().sendFailure(Component.translatable("freetrade.error.not_currency", only.getDescription()));
                return 0;
            }
            amount = (requested / v) * v;
        } else {
            amount = requested - Currency.change(requested).remainderMilli();
        }
        if (amount <= 0) {
            c.getSource().sendFailure(Component.translatable("freetrade.account.too_small"));
            return 0;
        }
        final long take = amount;
        later(c.getSource(), m.adjustAccount(actor(c.getSource()), acc, -take, true), v -> {
            if (v == null) return Component.translatable("freetrade.account.insufficient", acc);
            if (to != null) {
                List<ItemStack> stacks;
                if (only != null) {
                    stacks = new ArrayList<>();
                    long n = take / Currency.valueOf(only);
                    while (n > 0) {
                        int k = (int) Math.min(only.getDefaultMaxStackSize(), n);
                        stacks.add(new ItemStack(only, k));
                        n -= k;
                    }
                } else {
                    stacks = Currency.change(take).stacks();
                }
                Give.toPlayer(to, stacks);
            }
            return Component.translatable("freetrade.account.withdrawn", Money.format(take), acc, Money.format(v));
        });
        return (int) Math.min(Integer.MAX_VALUE, Money.floorUnits(take));
    }

    // ------------------------------------------------------------------ triggers

    private static ArgumentBuilder<CommandSourceStack, ?> trigger() {
        return Commands.literal("trigger").requires(s -> s.hasPermission(2))
                .then(Commands.literal("list").executes(FreeTradeCommand::triggerList))
                .then(Commands.literal("remove").then(Commands.argument("id", StringArgumentType.word()).suggests(TRIGGER_IDS)
                        .executes(c -> {
                            Market m = market(c);
                            String id = StringArgumentType.getString(c, "id");
                            later(c.getSource(), Triggers.remove(m, id), ok -> ok
                                    ? Component.translatable("freetrade.trigger.removed", id)
                                    : Component.translatable("freetrade.trigger.unknown", id));
                            return 1;
                        })))
                .then(Commands.literal("reset").then(Commands.argument("id", StringArgumentType.word()).suggests(TRIGGER_IDS)
                        .executes(c -> setTrigger(c, t -> new MarketStore.Trigger(t.id(), t.account(), t.op(), t.threshold(),
                                t.cost(), t.mode(), t.cooldownMs(), t.command(), 0, 0, true, true)))))
                .then(Commands.literal("enable").then(Commands.argument("id", StringArgumentType.word()).suggests(TRIGGER_IDS)
                        .executes(c -> setTrigger(c, t -> new MarketStore.Trigger(t.id(), t.account(), t.op(), t.threshold(),
                                t.cost(), t.mode(), t.cooldownMs(), t.command(), t.fired(), t.lastFired(), t.armed(), true)))))
                .then(Commands.literal("disable").then(Commands.argument("id", StringArgumentType.word()).suggests(TRIGGER_IDS)
                        .executes(c -> setTrigger(c, t -> new MarketStore.Trigger(t.id(), t.account(), t.op(), t.threshold(),
                                t.cost(), t.mode(), t.cooldownMs(), t.command(), t.fired(), t.lastFired(), t.armed(), false)))))
                .then(triggerAddNode());
    }

    /**
     * Comparison operators are literal nodes: Brigadier's unquoted words cannot contain '>', '<' or '='.
     */
    static final List<String> OPERATORS = List.of(">=", ">", "<=", "<", "=");

    private static ArgumentBuilder<CommandSourceStack, ?> queryNode(String fixed) {
        var query = Commands.literal("query").executes(c -> balance(c, accountName(c, fixed)));
        for (String op : OPERATORS) {
            query.then(Commands.literal(op).then(Commands.argument("value", DoubleArgumentType.doubleArg())
                    .executes(c -> query(c, accountName(c, fixed), op))));
        }
        return query;
    }

    private static ArgumentBuilder<CommandSourceStack, ?> triggerAddNode() {
        var account = Commands.argument("account", StringArgumentType.word()).suggests(ACCOUNTS);
        for (String op : OPERATORS) {
            var full = Commands.argument("cost", DoubleArgumentType.doubleArg(0))
                    .then(Commands.argument("cooldown", IntegerArgumentType.integer(0))
                            .then(Commands.argument("command", StringArgumentType.greedyString())
                                    .executes(c -> triggerAdd(c, op, DoubleArgumentType.getDouble(c, "cost"),
                                            IntegerArgumentType.getInteger(c, "cooldown")))));
            var shortForm = Commands.literal("run").then(Commands.argument("command", StringArgumentType.greedyString())
                    .executes(c -> triggerAdd(c, op, 0, 0)));
            var mode = Commands.argument("mode", StringArgumentType.word()).suggests(MODES).then(shortForm).then(full);
            account.then(Commands.literal(op).then(Commands.argument("value", DoubleArgumentType.doubleArg()).then(mode)));
        }
        return Commands.literal("add").then(Commands.argument("id", StringArgumentType.word()).then(account));
    }

    private static int triggerAdd(CommandContext<CommandSourceStack> c, String op, double cost, int cooldownSeconds)
            throws CommandSyntaxException {
        Market m = market(c);
        String id = StringArgumentType.getString(c, "id");
        String mode = StringArgumentType.getString(c, "mode").toLowerCase(Locale.ROOT);
        if (!List.of(">=", ">", "<=", "<", "=").contains(op) || !List.of("once", "repeat").contains(mode)) {
            c.getSource().sendFailure(Component.translatable("freetrade.trigger.syntax"));
            return 0;
        }
        double v = DoubleArgumentType.getDouble(c, "value");
        long threshold = Money.ofDouble(Math.abs(v)) * (v < 0 ? -1 : 1);
        var t = new MarketStore.Trigger(id, StringArgumentType.getString(c, "account"), op, threshold, Money.ofDouble(cost),
                mode, cooldownSeconds * 1000L, StringArgumentType.getString(c, "command"), 0, 0, true, true);
        later(c.getSource(), Triggers.save(m, t), x -> Component.translatable("freetrade.trigger.saved", id));
        return 1;
    }

    private static int setTrigger(CommandContext<CommandSourceStack> c,
                                  java.util.function.UnaryOperator<MarketStore.Trigger> change) throws CommandSyntaxException {
        Market m = market(c);
        String id = StringArgumentType.getString(c, "id");
        for (MarketStore.Trigger t : Triggers.list()) {
            if (t.id().equals(id)) {
                later(c.getSource(), Triggers.save(m, change.apply(t)), x -> Component.translatable("freetrade.trigger.saved", id));
                return 1;
            }
        }
        c.getSource().sendFailure(Component.translatable("freetrade.trigger.unknown", id));
        return 0;
    }

    private static int triggerList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        market(c);
        List<MarketStore.Trigger> l = Triggers.list();
        if (l.isEmpty()) reply(c.getSource(), Component.translatable("freetrade.trigger.none"));
        for (MarketStore.Trigger t : l) {
            reply(c.getSource(), Component.literal(String.format(Locale.ROOT,
                    "%s%s: %s %s %s | %s cost=%s cooldown=%ds fired=%d -> /%s", t.enabled() ? "" : "[off] ", t.id(),
                    t.account(), t.op(), Money.format(t.threshold()), t.mode(), Money.format(t.cost()),
                    t.cooldownMs() / 1000, t.fired(), t.command())));
        }
        return l.size();
    }
}
