package dev.freetrade.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import dev.freetrade.market.Money;
import dev.freetrade.menu.TraderMenu;
import dev.freetrade.net.Catalog;
import dev.freetrade.net.ClientHooks;
import dev.freetrade.net.FreeTradeNetwork;
import dev.freetrade.util.ItemKeys;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * The trade representative's window. Left: the sell grid (one kind of item) and the Sell button. Right: the
 * searchable catalog to buy from, with quantity, cost estimate and the Buy button.
 */
public class TraderScreen extends AbstractContainerScreen<TraderMenu> {
    private static final int MAX_QTY = 36 * 64;
    private static final int RX = 180; // right panel x (relative)

    private CatalogWidget catalog;
    private EditBox search;
    private Button allBtn, inStockBtn, outOfStockBtn;
    private Button minus64, minus1, plus1, plus64;
    private Button sellBtn, buyBtn, refreshBtn, bankBtn;
    private int quantity = 1;
    private boolean bank;
    private FreeTradeNetwork.CatalogPayload lastApplied;

    public TraderScreen(TraderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = 352;
        this.imageHeight = 186;
    }

    @Override
    protected void init() {
        super.init();
        int x = leftPos, y = topPos;
        if (catalog == null) catalog = new CatalogWidget(font, 0, 0, 0, 0, true);
        catalog.setBounds(x + RX, y + 50, 168, 84);

        search = addRenderableWidget(new EditBox(font, x + RX, y + 18, 168, 14,
                Component.translatable("freetrade.gui.trader.search")));
        search.setHint(Component.translatable("freetrade.gui.trader.search"));
        search.setResponder(q -> catalog.setQuery(q));

        allBtn = addRenderableWidget(Button.builder(Component.translatable("freetrade.gui.trader.filter_all"),
                        b -> catalog.setStockFilter(CatalogWidget.StockFilter.ALL))
                .bounds(x + RX, y + 34, 40, 12).build());
        inStockBtn = addRenderableWidget(Button.builder(Component.translatable("freetrade.gui.trader.filter_in"),
                        b -> catalog.setStockFilter(CatalogWidget.StockFilter.IN_STOCK))
                .bounds(x + RX + 42, y + 34, 64, 12).build());
        outOfStockBtn = addRenderableWidget(Button.builder(Component.translatable("freetrade.gui.trader.filter_out"),
                        b -> catalog.setStockFilter(CatalogWidget.StockFilter.OUT_OF_STOCK))
                .bounds(x + RX + 108, y + 34, 60, 12).build());

        minus64 = addRenderableWidget(Button.builder(Component.literal("-64"), b -> setQuantity(quantity - 64))
                .bounds(x + RX, y + 138, 30, 14).build());
        minus1 = addRenderableWidget(Button.builder(Component.literal("-1"), b -> setQuantity(quantity - 1))
                .bounds(x + RX + 32, y + 138, 26, 14).build());
        plus1 = addRenderableWidget(Button.builder(Component.literal("+1"), b -> setQuantity(quantity + 1))
                .bounds(x + RX + 100, y + 138, 26, 14).build());
        plus64 = addRenderableWidget(Button.builder(Component.literal("+64"), b -> setQuantity(quantity + 64))
                .bounds(x + RX + 128, y + 138, 40, 14).build());

        sellBtn = addRenderableWidget(Button.builder(Component.translatable("freetrade.gui.trader.sell"), b -> sell())
                .bounds(x + 8, y + 86, 50, 14)
                .tooltip(Tooltip.create(Component.translatable("freetrade.gui.trader.sell_tip")))
                .build());
        buyBtn = addRenderableWidget(Button.builder(Component.translatable("freetrade.gui.trader.buy"), b -> buy())
                .bounds(x + RX, y + 174, 66, 12)
                .tooltip(Tooltip.create(Component.translatable("freetrade.gui.trader.buy_tip")))
                .build());
        refreshBtn = addRenderableWidget(Button.builder(Component.translatable("freetrade.gui.trader.refresh"),
                        b -> PacketDistributor.sendToServer(new FreeTradeNetwork.CatalogRequest(false)))
                .bounds(x + 248, y + 174, 48, 12)
                .tooltip(Tooltip.create(Component.translatable("freetrade.gui.trader.refresh_tip")))
                .build());
        bankBtn = addRenderableWidget(Button.builder(Component.translatable("freetrade.gui.trader.bank_off"),
                        b -> bank = !bank)
                .bounds(x + 298, y + 174, 50, 12)
                .tooltip(Tooltip.create(Component.translatable("freetrade.gui.trader.bank_tip")))
                .build());
        catalogFromLast();
    }

    private void catalogFromLast() {
        FreeTradeNetwork.CatalogPayload p = ClientHooks.lastCatalog;
        if (p != null) {
            lastApplied = p;
            catalog.setEntries(p.entries());
        }
    }

    private void setQuantity(int q) {
        quantity = Math.max(1, Math.min(MAX_QTY, q));
    }

    private void sell() {
        PacketDistributor.sendToServer(new FreeTradeNetwork.TraderAction(menu.containerId, true, "", 0, bank));
    }

    private void buy() {
        Catalog.Entry sel = catalog.selected();
        if (sel == null || sel.buy() < 0) return;
        PacketDistributor.sendToServer(new FreeTradeNetwork.TraderAction(menu.containerId, false, sel.item(), quantity, bank));
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        FreeTradeNetwork.CatalogPayload p = ClientHooks.lastCatalog;
        if (p != null && p != lastApplied) {
            lastApplied = p;
            catalog.setEntries(p.entries());
        }
        boolean busy = menu.busy();
        sellBtn.active = !busy && !menu.gridKind().isEmpty();
        Catalog.Entry sel = catalog.selected();
        buyBtn.active = !busy && sel != null && sel.buy() >= 0;
        CatalogWidget.StockFilter f = catalog.stockFilter();
        allBtn.active = f != CatalogWidget.StockFilter.ALL;
        inStockBtn.active = f != CatalogWidget.StockFilter.IN_STOCK;
        outOfStockBtn.active = f != CatalogWidget.StockFilter.OUT_OF_STOCK;
        bankBtn.visible = ClientHooks.bankAvailable;
        bankBtn.setMessage(Component.translatable(bank ? "freetrade.gui.trader.bank_on" : "freetrade.gui.trader.bank_off"));
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // typing in the search box must not trigger the inventory key (which closes the screen)
        if (search != null && search.isFocused() && keyCode != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (catalog.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (catalog.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        MachineScreen.panel(g, x, y, imageWidth, imageHeight);
        g.fill(x + 176, y + 4, x + 177, y + imageHeight - 4, 0xFF8B8B8B);
        for (Slot s : menu.slots) MachineScreen.slotBg(g, x + s.x - 1, y + s.y - 1);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // only the title: the player inventory label would collide with the Sell button
        g.drawString(font, title, titleLabelX, titleLabelY, 0x404040, false);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        super.render(g, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        int dark = 0x404040;

        // left: sell grid
        g.drawString(font, Component.translatable("freetrade.gui.trader.sell_label"), x + 8, y + 18, dark, false);
        g.drawString(font, font.plainSubstrByWidth(sellInfo(), 108), x + 62, y + 89, dark, false);

        // right: header, quantity and selection
        g.drawString(font, Component.translatable("freetrade.gui.trader.buy"), x + RX, y + 6, dark, false);
        catalog.render(g, mouseX, mouseY);
        g.drawCenteredString(font, String.valueOf(quantity), x + RX + 79, y + 141, dark);

        Catalog.Entry sel = catalog.selected();
        if (sel == null) {
            g.drawString(font, Component.translatable("freetrade.gui.trader.select_hint"), x + RX + 20, y + 158, dark, false);
        } else {
            g.renderItem(ItemKeys.stack(sel.item(), 1), x + RX, y + 155);
            g.drawString(font, font.plainSubstrByWidth(CatalogWidget.name(sel).getString(), 146),
                    x + RX + 20, y + 155, dark, false);
            if (sel.buy() < 0) {
                g.drawString(font, Component.translatable("freetrade.gui.trader.out_of_stock"), x + RX + 20, y + 165, 0x8B0000, false);
            } else {
                long cost = (long) Math.min(Long.MAX_VALUE / 2, (double) sel.buy() * quantity);
                g.drawString(font, Component.translatable("freetrade.gui.trader.cost", Money.format(cost)),
                        x + RX + 20, y + 165, dark, false);
            }
        }

        List<Component> tip = catalog.tooltip(mouseX, mouseY);
        if (tip != null) g.renderComponentTooltip(font, tip, mouseX, mouseY);
    }

    /** "~ X U.M.  (kind xN)" for the sell grid; the estimate uses the catalog's sell price when known. */
    private String sellInfo() {
        ItemStack kind = menu.gridKind();
        if (kind.isEmpty()) return Component.translatable("freetrade.gui.trader.grid_empty").getString();
        String base = Component.translatable("freetrade.gui.trader.grid_info", kind.getHoverName(), menu.gridCount()).getString();
        Catalog.Entry e = entryFor(ItemKeys.id(kind));
        if (e == null || e.sell() <= 0) return base;
        long est = e.sell() * menu.gridCount();
        return Component.translatable("freetrade.gui.trader.estimate", Money.format(est)).getString() + "  " + base;
    }

    private static Catalog.Entry entryFor(String id) {
        FreeTradeNetwork.CatalogPayload p = ClientHooks.lastCatalog;
        if (p == null) return null;
        for (Catalog.Entry e : p.entries()) if (e.item().equals(id)) return e;
        return null;
    }
}
