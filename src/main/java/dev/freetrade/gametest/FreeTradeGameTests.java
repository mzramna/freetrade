package dev.freetrade.gametest;

import dev.freetrade.FreeTrade;
import dev.freetrade.block.AutoBuyerBlockEntity;
import dev.freetrade.block.MarketStorageBlockEntity;
import dev.freetrade.block.SellPointBlockEntity;
import dev.freetrade.config.FreeTradeConfig;
import dev.freetrade.currency.Currency;
import dev.freetrade.market.Market;
import dev.freetrade.registry.ModRegistries;
import dev.freetrade.server.FreeTradeServer;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * Runs with ./gradlew runGameTestServer. Each test works inside the 5x3x5 "empty" template; positions are
 * relative to the test origin. Machine tests set the machine interval to 1 tick and restore it when done.
 */
@GameTestHolder(FreeTrade.MODID)
@PrefixGameTestTemplate(false)
public class FreeTradeGameTests {
    private static final BlockPos MACHINE = new BlockPos(2, 1, 2);
    private static final BlockPos REDSTONE = new BlockPos(1, 1, 2);
    private static final long BASE_CAPACITY = 64L * MarketStorageBlockEntity.BASE_STACKS;

    private static Market liveMarket(GameTestHelper h) {
        Market m = FreeTradeServer.market();
        h.assertTrue(m != null, "Free Trade market is not open (the database failed to start?)");
        return m;
    }

    private static String status(MarketStorageBlockEntity be) {
        return "status code " + be.data.get(22);
    }

    private static void restoreMachineInterval() {
        FreeTradeConfig.machineIntervalTicks = 20;
    }

    /** Waits (retrying the check each tick) until it passes, then restores the machine interval. */
    private static void succeedEventually(GameTestHelper h, Runnable check) {
        h.succeedWhen(() -> {
            check.run();
            restoreMachineInterval();
        });
    }

    @GameTest(template = "empty")
    public static void marketIsOpen(GameTestHelper h) {
        Market m = liveMarket(h);
        h.assertTrue(m.isTradeable("minecraft:oak_log"), "minecraft:oak_log should be tradeable");
        double unit = m.unitPrice("minecraft:oak_log");
        h.assertTrue(unit > 0, "oak_log unit price should be > 0, was " + unit);

        double log = m.baseValue("minecraft:oak_log");
        double planks = m.baseValue("minecraft:oak_planks");
        h.assertTrue(log > 0, "oak_log base value should be > 0, was " + log);
        double expected = log / 4.0;
        h.assertTrue(Math.abs(planks - expected) <= expected * 0.10,
                "oak_planks base value should be about oak_log/4 = " + expected + ", was " + planks);
        h.succeed();
    }

    @GameTest(template = "empty")
    public static void emeraldIsMoney(GameTestHelper h) {
        Market m = liveMarket(h);
        h.assertTrue(Currency.isCurrency(Items.EMERALD), "emerald should be a currency item");
        h.assertValueEqual(Currency.valueOf(Items.EMERALD), 1000L, "emerald value in milli-U.M.");
        h.assertTrue(!m.isTradeable("minecraft:emerald"), "emerald must not be tradeable as goods");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sellPointAbsorbsAndSellsWithRedstone(GameTestHelper h) {
        Market m = liveMarket(h);
        FreeTradeConfig.machineIntervalTicks = 1;
        h.setBlock(MACHINE, ModRegistries.SELL_POINT.get());
        SellPointBlockEntity be = h.getBlockEntity(MACHINE);
        h.assertTrue(m.isTradeable("minecraft:cobblestone"), "cobblestone should be tradeable");
        h.assertTrue(be.setTarget(new ItemStack(Items.COBBLESTONE)), "setTarget should accept cobblestone");

        ItemStack rest = be.itemHandler(null).insertItem(0, new ItemStack(Items.COBBLESTONE, 64), false);
        h.assertTrue(rest.isEmpty(), "all 64 cobblestone should be absorbed, " + rest.getCount() + " left over");
        h.assertValueEqual(be.stored(), 64L, "stored after insert");

        h.setBlock(REDSTONE, Blocks.REDSTONE_BLOCK);
        succeedEventually(h, () -> {
            h.assertTrue(be.stored() == 0, "stored should drain while powered, is " + be.stored() + " (" + status(be) + ")");
            h.assertTrue(be.balance() > 0, "balance should be paid after the sale, is " + be.balance() + " (" + status(be) + ")");
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void sellPointMoneyExtractionNeedsRedstone(GameTestHelper h) {
        h.setBlock(MACHINE, ModRegistries.SELL_POINT.get());
        SellPointBlockEntity be = h.getBlockEntity(MACHINE);
        IItemHandler hd = be.itemHandler(null);

        ItemStack rest = hd.insertItem(0, new ItemStack(Items.EMERALD, 5), false);
        h.assertTrue(rest.isEmpty(), "5 emeralds should become balance, " + rest.getCount() + " refused");
        h.assertValueEqual(be.balance(), 5000L, "balance after depositing 5 emeralds");
        // default selection is the emerald block; switch to plain emeralds
        for (int i = 0; i < 10 && be.selectedCurrency() != Items.EMERALD; i++) be.cycleCurrency(1);
        h.assertTrue(be.selectedCurrency() == Items.EMERALD, "selected currency should be emerald");

        h.assertTrue(hd.extractItem(1, 64, false).isEmpty(), "money must not come out without redstone");

        h.setBlock(REDSTONE, Blocks.REDSTONE_BLOCK);
        h.runAfterDelay(5, () -> {
            h.assertTrue(be.powered(), "sell point should be powered by the adjacent redstone block");
            ItemStack got = hd.extractItem(1, 64, false);
            h.assertTrue(got.is(Items.EMERALD) && got.getCount() == 5, "expected 5 emeralds out, got " + got);
            h.assertValueEqual(be.balance(), 0L, "balance after extraction");
            h.succeed();
        });
    }

    @GameTest(template = "empty")
    public static void changeMachine(GameTestHelper h) {
        h.setBlock(MACHINE, ModRegistries.SELL_POINT.get());
        SellPointBlockEntity be = h.getBlockEntity(MACHINE);
        ItemStack rest = be.itemHandler(null).insertItem(0, new ItemStack(Items.EMERALD_BLOCK, 1), false);
        h.assertTrue(rest.isEmpty(), "emerald block should be accepted by the change machine");
        h.assertValueEqual(be.balance(), 9000L, "balance after one emerald block");

        for (int i = 0; i < 10 && be.selectedCurrency() != Items.EMERALD; i++) be.cycleCurrency(1);
        h.assertTrue(be.selectedCurrency() == Items.EMERALD, "selected currency should be emerald");

        ItemStack got = be.withdrawCurrency(Items.EMERALD, 64, false);
        h.assertTrue(got.is(Items.EMERALD) && got.getCount() == 9, "expected 9 emeralds, got " + got);
        h.assertValueEqual(be.balance(), 0L, "balance after withdrawal");
        h.succeed();
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void barrelsMultiplyCapacity(GameTestHelper h) {
        liveMarket(h);
        h.setBlock(MACHINE, ModRegistries.SELL_POINT.get());
        SellPointBlockEntity be = h.getBlockEntity(MACHINE);
        h.assertTrue(be.setTarget(new ItemStack(Items.COBBLESTONE)), "setTarget should accept cobblestone");
        h.assertValueEqual(be.capacity(), BASE_CAPACITY, "capacity without neighbours");

        // two barrels next to the machine: upgrades recount every 20 ticks
        h.setBlock(new BlockPos(3, 1, 2), Blocks.BARREL);
        h.setBlock(new BlockPos(2, 1, 1), Blocks.BARREL);
        h.runAfterDelay(25, () -> {
            h.assertValueEqual(be.upgrades(), 2, "upgrades with two barrels");
            h.assertValueEqual(be.capacity(), BASE_CAPACITY * 3, "capacity with two barrels");

            // fill all six sides: upgrades are capped at 5
            h.setBlock(new BlockPos(2, 1, 3), Blocks.BARREL);
            h.setBlock(new BlockPos(2, 0, 2), Blocks.BARREL);
            h.setBlock(new BlockPos(2, 2, 2), Blocks.BARREL);
            h.setBlock(REDSTONE, Blocks.BARREL);
            h.runAfterDelay(25, () -> {
                h.assertValueEqual(be.upgrades(), 5, "upgrades with six barrels (capped)");
                h.assertValueEqual(be.capacity(), BASE_CAPACITY * 6, "capacity with capped upgrades");
                h.succeed();
            });
        });
    }

    @GameTest(template = "empty", timeoutTicks = 200)
    public static void autoBuyerBuys(GameTestHelper h) {
        Market m = liveMarket(h);
        FreeTradeConfig.machineIntervalTicks = 1;
        h.setBlock(MACHINE, ModRegistries.AUTO_BUYER.get());
        AutoBuyerBlockEntity be = h.getBlockEntity(MACHINE);
        h.assertTrue(m.isTradeable("minecraft:oak_log"), "oak_log should be tradeable");
        h.assertTrue(be.setTarget(new ItemStack(Items.OAK_LOG)), "setTarget should accept oak_log");

        IItemHandler hd = be.itemHandler(null);
        ItemStack rest = hd.insertItem(0, new ItemStack(Items.EMERALD, 64), false);
        h.assertTrue(rest.isEmpty(), "64 emeralds should be accepted, " + rest.getCount() + " refused");
        h.assertValueEqual(be.balance(), 64_000L, "balance after 64 emeralds");

        h.assertTrue(hd.getStackInSlot(1).isEmpty(), "bought logs must not be visible to automation without redstone");

        h.setBlock(REDSTONE, Blocks.REDSTONE_BLOCK);
        h.succeedWhen(() -> {
            h.assertTrue(be.stored() > 0, "auto buyer should have bought logs, stored " + be.stored() + " (" + status(be) + ")");
            h.assertTrue(be.balance() < 64_000L, "balance should be spent, is " + be.balance());
            h.assertTrue(be.powered(), "auto buyer should be powered by the adjacent redstone block");
            ItemStack got = hd.extractItem(1, 64, false);
            h.assertTrue(got.is(Items.OAK_LOG) && !got.isEmpty(), "expected oak logs out, got " + got);
            restoreMachineInterval();
        });
    }

    @GameTest(template = "empty")
    public static void currencyRefusedAsTarget(GameTestHelper h) {
        liveMarket(h);
        h.setBlock(MACHINE, ModRegistries.SELL_POINT.get());
        SellPointBlockEntity be = h.getBlockEntity(MACHINE);
        h.assertTrue(!be.setTarget(new ItemStack(Items.EMERALD)), "money must be refused as a target");
        h.assertTrue(be.target().isEmpty(), "target should stay empty, is " + be.target());
        h.succeed();
    }

    /** Operators are literal nodes: every form must parse completely. */
    @GameTest(template = "empty")
    public static void accountAndTriggerCommandsParse(GameTestHelper h) {
        var server = h.getLevel().getServer();
        var dispatcher = server.getCommands().getDispatcher();
        var source = server.createCommandSourceStack().withPermission(4);
        for (String cmd : new String[]{
                "freetrade account treasury query >= 1000",
                "ft taxaccount query < 5.5",
                "ft treasury query = 0",
                "ft trigger add goal tax >= 500 once run say Tax goal reached",
                "ft trigger add reward tax > 10 repeat 10 60 give @a minecraft:diamond 1"}) {
            var parse = dispatcher.parse(cmd, source);
            h.assertTrue(parse.getReader().getRemainingLength() == 0 && parse.getExceptions().isEmpty()
                    && parse.getContext().getLastChild().getCommand() != null, "command should parse: " + cmd);
        }
        h.succeed();
    }

    /** A trigger on the tax account fires its command and takes its cost from the account. */
    @GameTest(template = "empty", timeoutTicks = 200)
    public static void triggerFiresAndTakesCost(GameTestHelper h) {
        Market m = liveMarket(h);
        var server = h.getLevel().getServer();
        var source = server.createCommandSourceStack().withPermission(4).withSuppressedOutput();
        BlockPos target = h.absolutePos(new BlockPos(2, 1, 2));
        long before = m.account(dev.freetrade.db.MarketStore.TAX);
        long threshold = before / 1000 + 5;
        server.getCommands().performPrefixedCommand(source, "ft trigger remove gt_trigger");
        server.getCommands().performPrefixedCommand(source, "ft trigger add gt_trigger tax >= " + threshold
                + " once 3 0 setblock " + target.getX() + " " + target.getY() + " " + target.getZ() + " minecraft:gold_block");
        server.getCommands().performPrefixedCommand(source, "ft taxaccount deposit 10");
        h.succeedWhen(() -> {
            h.assertTrue(h.getLevel().getBlockState(target).is(Blocks.GOLD_BLOCK), "trigger command should have placed a gold block");
            h.assertValueEqual(m.account(dev.freetrade.db.MarketStore.TAX), before + 10_000 - 3_000, "tax balance after cost");
        });
    }
}
