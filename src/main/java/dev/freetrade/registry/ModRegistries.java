package dev.freetrade.registry;

import com.google.common.collect.ImmutableSet;

import dev.freetrade.FreeTrade;
import dev.freetrade.block.AutoBuyerBlock;
import dev.freetrade.block.AutoBuyerBlockEntity;
import dev.freetrade.block.SellPointBlock;
import dev.freetrade.block.SellPointBlockEntity;
import dev.freetrade.item.TradeGuideItem;
import dev.freetrade.menu.AutoBuyerMenu;
import dev.freetrade.menu.SellPointMenu;
import dev.freetrade.menu.TraderMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.ai.village.poi.PoiType;
import net.minecraft.world.entity.npc.villager.VillagerProfession;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.extensions.IMenuTypeExtension;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public final class ModRegistries {
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FreeTrade.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FreeTrade.MODID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, FreeTrade.MODID);
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(Registries.MENU, FreeTrade.MODID);
    public static final DeferredRegister<PoiType> POI = DeferredRegister.create(Registries.POINT_OF_INTEREST_TYPE, FreeTrade.MODID);
    public static final DeferredRegister<VillagerProfession> PROFESSIONS =
            DeferredRegister.create(Registries.VILLAGER_PROFESSION, FreeTrade.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, FreeTrade.MODID);

    private static BlockBehaviour.Properties machine() {
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2.5f, 1200f).sound(SoundType.WOOD);
    }

    public static final DeferredBlock<SellPointBlock> SELL_POINT = BLOCKS.register("sell_point", () -> new SellPointBlock(machine()));
    public static final DeferredBlock<AutoBuyerBlock> AUTO_BUYER = BLOCKS.register("auto_buyer", () -> new AutoBuyerBlock(machine()));

    public static final DeferredItem<BlockItem> SELL_POINT_ITEM = ITEMS.registerSimpleBlockItem(SELL_POINT);
    public static final DeferredItem<BlockItem> AUTO_BUYER_ITEM = ITEMS.registerSimpleBlockItem(AUTO_BUYER);
    public static final DeferredItem<TradeGuideItem> TRADE_GUIDE = ITEMS.register("trade_guide",
            () -> new TradeGuideItem(new Item.Properties().stacksTo(1).rarity(Rarity.UNCOMMON)));

    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<SellPointBlockEntity>> SELL_POINT_BE =
            BLOCK_ENTITIES.register("sell_point", () -> new BlockEntityType<>(SellPointBlockEntity::new, SELL_POINT.get()));
    @SuppressWarnings("DataFlowIssue")
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<AutoBuyerBlockEntity>> AUTO_BUYER_BE =
            BLOCK_ENTITIES.register("auto_buyer", () -> new BlockEntityType<>(AutoBuyerBlockEntity::new, AUTO_BUYER.get()));

    public static final DeferredHolder<MenuType<?>, MenuType<SellPointMenu>> SELL_POINT_MENU =
            MENUS.register("sell_point", () -> IMenuTypeExtension.create(SellPointMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<AutoBuyerMenu>> AUTO_BUYER_MENU =
            MENUS.register("auto_buyer", () -> IMenuTypeExtension.create(AutoBuyerMenu::new));
    public static final DeferredHolder<MenuType<?>, MenuType<TraderMenu>> TRADER_MENU =
            MENUS.register("trader", () -> new MenuType<>(TraderMenu::new, FeatureFlags.DEFAULT_FLAGS));

    public static final DeferredHolder<PoiType, PoiType> TRADE_POI = POI.register("trade_representative",
            () -> new PoiType(ImmutableSet.copyOf(AUTO_BUYER.get().getStateDefinition().getPossibleStates()), 1, 1));

    public static final DeferredHolder<VillagerProfession, VillagerProfession> TRADE_REPRESENTATIVE =
            PROFESSIONS.register("trade_representative", () -> new VillagerProfession(Component.translatable("entity.minecraft.villager.trade_representative"),
                    h -> h.is(TRADE_POI.getKey()), h -> h.is(TRADE_POI.getKey()), ImmutableSet.of(), ImmutableSet.of(),
                    SoundEvents.VILLAGER_WORK_CARTOGRAPHER, new it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap<>()));

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> TAB = TABS.register("main", () -> CreativeModeTab.builder()
            .title(Component.translatable("itemGroup.freetrade"))
            .icon(() -> new ItemStack(TRADE_GUIDE.get()))
            .displayItems((p, out) -> {
                out.accept(TRADE_GUIDE.get());
                out.accept(SELL_POINT_ITEM.get());
                out.accept(AUTO_BUYER_ITEM.get());
            })
            .build());

    private ModRegistries() {}

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        MENUS.register(bus);
        POI.register(bus);
        PROFESSIONS.register(bus);
        TABS.register(bus);
    }
}
