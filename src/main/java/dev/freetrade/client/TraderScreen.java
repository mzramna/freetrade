package dev.freetrade.client;

import java.util.List;

import org.lwjgl.glfw.GLFW;

import dev.freetrade.market.Money;
import dev.freetrade.menu.TraderMenu;
import dev.freetrade.net.Catalog;
import dev.freetrade.net.ClientHooks;
import dev.freetrade.net.FreeTradeNetwork;
import dev.freetrade.util.ItemKeys;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

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
        super(menu, inv, title, 352, 186);
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
                        b -> ClientPacketDistributor.sendToServer(new FreeTradeNetwork.CatalogRequest(false)))
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
        ClientPacketDistributor.sendToServer(new FreeTradeNetwork.TraderAction(menu.containerId, true, "", 0, bank));
    }

    private void buy() {
        Catalog.Entry sel = catalog.selected();
        if (sel == null || sel.buy() < 0) return;
        ClientPacketDistributor.sendToServer(new FreeTradeNetwork.TraderAction(menu.containerId, false, sel.item(), quantity, bank));
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
    public boolean keyPressed(KeyEvent event) {
        // typing in the search box must not trigger the inventory key (which closes the screen)
        if (search != null && search.isFocused() && event.key() != GLFW.GLFW_KEY_ESCAPE) {
            search.keyPressed(event);
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (catalog.mouseClicked(event.x(), event.y(), event.button())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (catalog.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    // ------------------------------------------------------------------ drawing

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(g, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        MachineScreen.panel(g, x, y, imageWidth, imageHeight);
        g.fill(x + 176, y + 4, x + 177, y + imageHeight - 4, 0xFF8B8B8B);
        for (Slot s : menu.slots) MachineScreen.slotBg(g, x + s.x - 1, y + s.y - 1);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        // only the title: the player inventory label would collide with the Sell button
        g.text(font, title, titleLabelX, titleLabelY, 0xFF404040, false);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(g, mouseX, mouseY, partialTick);
        int x = leftPos, y = topPos;
        int dark = 0xFF404040;

        // left: sell grid
        g.text(font, Component.translatable("freetrade.gui.trader.sell_label"), x + 8, y + 18, dark, false);
        int ly = y + 85;
        for (var line : font.split(Component.literal(sellInfo()), 108).stream().limit(2).toList()) {
            g.text(font, line, x + 62, ly, dark, false);
            ly += 9;
        }

        // right: header, quantity and selection
        g.text(font, Component.translatable("freetrade.gui.trader.buy"), x + RX, y + 6, dark, false);
        catalog.render(g, mouseX, mouseY);
        g.centeredText(font, String.valueOf(quantity), x + RX + 79, y + 141, dark);

        Catalog.Entry sel = catalog.selected();
        if (sel == null) {
            g.text(font, Component.translatable("freetrade.gui.trader.select_hint"), x + RX + 20, y + 158, dark, false);
        } else {
            g.item(ItemKeys.stack(sel.item(), 1), x + RX, y + 155);
            g.text(font, font.plainSubstrByWidth(CatalogWidget.name(sel).getString(), 146),
                    x + RX + 20, y + 155, dark, false);
            if (sel.buy() < 0) {
                g.text(font, Component.translatable("freetrade.gui.trader.out_of_stock"), x + RX + 20, y + 165, 0xFF8B0000, false);
            } else {
                long cost = (long) Math.min(Long.MAX_VALUE / 2, (double) sel.buy() * quantity);
                g.text(font, Component.translatable("freetrade.gui.trader.cost", Money.format(cost)),
                        x + RX + 20, y + 165, dark, false);
            }
        }

        List<Component> tip = catalog.tooltip(mouseX, mouseY);
        if (tip != null) g.setComponentTooltipForNextFrame(font, tip, mouseX, mouseY);
    }

    /** "~ X U.M.  (kind xN)" for the sell grid; the estimate uses the catalog's sell price when known. */
    private String sellInfo() {
        ItemStack kind = menu.gridKind();
        if (kind.isEmpty()) return Component.translatable("freetrade.gui.trader.grid_empty").getString();
        String base = Component.translatable("freetrade.gui.trader.grid_info", kind.getHoverName(), menu.gridCount()).getString();
        Catalog.Entry e = entryFor(ItemKeys.id(kind));
        if (e == null || e.sell() <= 0) return base;
        long est = e.sell() * menu.gridCount();
        return Component.translatable("freetrade.gui.trader.estimate", Money.format(est)).getString() + " " + base;
    }

    private static Catalog.Entry entryFor(String id) {
        FreeTradeNetwork.CatalogPayload p = ClientHooks.lastCatalog;
        if (p == null) return null;
        for (Catalog.Entry e : p.entries()) if (e.item().equals(id)) return e;
        return null;
    }
}
