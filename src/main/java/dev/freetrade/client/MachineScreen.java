package dev.freetrade.client;

import java.util.ArrayList;
import java.util.List;

import dev.freetrade.block.MarketStorageBlockEntity;
import dev.freetrade.market.Money;
import dev.freetrade.menu.MachineMenu;
import dev.freetrade.menu.SellPointMenu;
import dev.freetrade.net.FreeTradeNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * GUI of the Sell Point and the Auto Buyer. Drawn without textures: a gray panel, slot squares under every menu
 * slot, a ghost "target item" box, a money selector (arrows above and below the money slot) and the status lines.
 */
public class MachineScreen<T extends MachineMenu> extends AbstractContainerScreen<T> {
    private static final int GHOST_X = 8, GHOST_Y = 18;
    private static final int MONEY_X = 140, MONEY_Y = 40;
    private static final int SIZE = 18;
    private static final int TEXT_X = 40, TEXT_W = 94;

    public MachineScreen(T menu, Inventory inv, Component title) {
        super(menu, inv, title, 176, 186);
        this.inventoryLabelY = 93;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        addRenderableWidget(Button.builder(Component.literal("^"), b -> press(MachineMenu.BTN_CURRENCY_UP))
                .bounds(x + MONEY_X, y + 28, SIZE, 12)
                .tooltip(Tooltip.create(Component.translatable("freetrade.gui.machine.currency")))
                .build());
        addRenderableWidget(Button.builder(Component.literal("v"), b -> press(MachineMenu.BTN_CURRENCY_DOWN))
                .bounds(x + MONEY_X, y + 60, SIZE, 12)
                .tooltip(Tooltip.create(Component.translatable("freetrade.gui.machine.currency")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("freetrade.gui.machine.all"),
                        b -> press(MachineMenu.BTN_WITHDRAW_ALL))
                .bounds(x + 46, y + 80, 40, 12)
                .tooltip(Tooltip.create(Component.translatable("freetrade.gui.machine.all_tip")))
                .build());
        addRenderableWidget(Button.builder(Component.translatable("freetrade.gui.machine.take"),
                        b -> press(MachineMenu.BTN_TAKE_STORED))
                .bounds(x + 90, y + 80, 40, 12)
                .tooltip(Tooltip.create(Component.translatable("freetrade.gui.machine.take_tip")))
                .build());
    }

    /** Screen-space area of the ghost target box, for JEI/EMI drag and drop. */
    public Rect2i ghostArea() {
        return new Rect2i(leftPos + GHOST_X, topPos + GHOST_Y, SIZE, SIZE);
    }

    /** Sets the ghost target to the given item (count ignored). Used by the JEI/EMI drag and drop handler. */
    public void acceptGhost(ItemStack stack) {
        if (stack.isEmpty()) return;
        sendTarget(stack.copyWithCount(1));
    }

    // ------------------------------------------------------------------ actions

    private void press(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void sendTarget(ItemStack stack) {
        ClientPacketDistributor.sendToServer(new FreeTradeNetwork.SetTarget(menu.containerId, stack));
    }

    private void ghostClick() {
        ItemStack carried = menu.getCarried();
        if (!carried.isEmpty()) sendTarget(carried.copyWithCount(1));
        else if (!menu.targetStack().isEmpty()) sendTarget(ItemStack.EMPTY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x(), mouseY = event.y();
        if (inside(mouseX, mouseY, leftPos + GHOST_X, topPos + GHOST_Y, SIZE, SIZE)) {
            ghostClick();
            return true;
        }
        if (inside(mouseX, mouseY, leftPos + MONEY_X, topPos + MONEY_Y, SIZE, SIZE)
                && menu.selectedCurrency() != Items.AIR) {
            boolean stack = event.button() == 1 || event.hasShiftDown();
            press(stack ? MachineMenu.BTN_WITHDRAW_STACK : MachineMenu.BTN_WITHDRAW_ONE);
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    private static boolean inside(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        panel(g, x, y, imageWidth, imageHeight);
        for (Slot s : menu.slots) slotBg(g, x + s.x - 1, y + s.y - 1);
        slotBg(g, x + GHOST_X, y + GHOST_Y);
        slotBg(g, x + MONEY_X, y + MONEY_Y);
        ItemStack target = menu.targetStack();
        if (!target.isEmpty()) g.item(target, x + GHOST_X + 1, y + GHOST_Y + 1);
        Item currency = menu.selectedCurrency();
        if (currency != Items.AIR) g.item(new ItemStack(currency), x + MONEY_X + 1, y + MONEY_Y + 1);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        super.extractLabels(g, mouseX, mouseY);
        int dark = 0xFF404040;
        // owner, top right
        String owner = font.plainSubstrByWidth(Component.translatable("freetrade.gui.machine.owner", menu.ownerName()).getString(), 80);
        g.text(font, owner, imageWidth - 8 - font.width(owner), 6, 0xFF707070, false);
        g.text(font, Component.translatable("freetrade.gui.machine.target_label"), 8, 38, dark, false);
        g.text(font, Component.translatable("freetrade.gui.machine.input"), 8, 66, dark, false);

        boolean sellPoint = menu instanceof SellPointMenu;
        line(g, Component.translatable("freetrade.gui.machine.stored", menu.stored(), menu.capacity()), 18, dark);
        line(g, Component.translatable("freetrade.gui.machine.multiplier", menu.upgrades() + 1), 27, dark);
        Component price = Component.translatable(sellPoint ? "freetrade.gui.machine.pays" : "freetrade.gui.machine.costs",
                Money.format(menu.unitPrice()));
        line(g, price, 36, dark);
        line(g, Component.translatable("freetrade.gui.machine.balance", Money.format(menu.balance())), 45, dark);

        int status = menu.status();
        int statusColor = status == MarketStorageBlockEntity.STATUS_IDLE ? dark
                : status == MarketStorageBlockEntity.STATUS_OK ? 0xFF006400
                : status == MarketStorageBlockEntity.STATUS_WAITING_REDSTONE ? 0xFF505050
                : 0xFF8B0000;
        g.text(font, Component.translatable("freetrade.gui.status." + statusKey(status)), 48, 58, statusColor, false);
        g.text(font, Component.translatable(menu.powered() ? "freetrade.gui.machine.redstone_on"
                : "freetrade.gui.machine.redstone_off"), 48, 67, menu.powered() ? 0xFF006400 : 0xFF505050, false);
    }

    private void line(GuiGraphicsExtractor g, Component text, int y, int color) {
        g.text(font, font.plainSubstrByWidth(text.getString(), TEXT_W), TEXT_X, y, color, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        List<Component> tip = null;
        if (inside(mouseX, mouseY, leftPos + GHOST_X, topPos + GHOST_Y, SIZE, SIZE)) {
            tip = new ArrayList<>();
            ItemStack target = menu.targetStack();
            tip.add(Component.translatable("freetrade.gui.machine.target"));
            tip.add(target.isEmpty() ? Component.translatable("freetrade.gui.machine.target_none")
                    : Component.translatable("freetrade.gui.machine.target_now", target.getHoverName()));
            tip.add(Component.translatable("freetrade.gui.machine.target_hint"));
        } else if (inside(mouseX, mouseY, leftPos + MONEY_X, topPos + MONEY_Y, SIZE, SIZE)
                && menu.selectedCurrency() != Items.AIR) {
            tip = List.of(new ItemStack(menu.selectedCurrency()).getHoverName(),
                    Component.translatable("freetrade.gui.machine.withdraw_hint"));
        }
        if (tip != null) g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
    }

    private static String statusKey(int s) {
        return switch (s) {
            case MarketStorageBlockEntity.STATUS_OK -> "ok";
            case MarketStorageBlockEntity.STATUS_NO_TARGET -> "no_target";
            case MarketStorageBlockEntity.STATUS_NOT_TRADEABLE -> "not_tradeable";
            case MarketStorageBlockEntity.STATUS_TREASURY -> "treasury";
            case MarketStorageBlockEntity.STATUS_NO_STOCK -> "no_stock";
            case MarketStorageBlockEntity.STATUS_NO_FUNDS -> "no_funds";
            case MarketStorageBlockEntity.STATUS_OFFLINE -> "offline";
            case MarketStorageBlockEntity.STATUS_FULL -> "full";
            case MarketStorageBlockEntity.STATUS_ERROR -> "error";
            case MarketStorageBlockEntity.STATUS_WAITING_REDSTONE -> "waiting_redstone";
            default -> "idle";
        };
    }

    /** Vanilla-like panel: light gray face with a bevel. */
    static void panel(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, 0xFF000000);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        g.fill(x + 1, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 2, y + h - 2, 0xFFFFFFFF);
        g.fill(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
        g.fill(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
    }

    /** 18x18 slot square; (x, y) is its top-left corner. */
    static void slotBg(GuiGraphicsExtractor g, int x, int y) {
        g.fill(x, y, x + 18, y + 18, 0xFF373737);
        g.fill(x + 1, y + 1, x + 18, y + 18, 0xFFFFFFFF);
        g.fill(x + 1, y + 1, x + 17, y + 17, 0xFF8B8B8B);
    }
}
