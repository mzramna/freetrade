package dev.freetrade.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import dev.freetrade.market.Money;
import dev.freetrade.net.FreeTradeNetwork;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/** The Trade Association guide book: a short "How it works" text and a searchable catalog of prices. */
public class GuideScreen extends Screen {
    public static final int PAGES = 7;

    private static final int PAPER = 0xFFF3E9D2;
    private static final int BORDER = 0xFF4A2E17;
    private static final int INK = 0xFF3B2A1A;
    private static final int MUTED = 0xFF7A6248;
    private static final int TAB_HOW = 0;
    private static final int TAB_CATALOG = 1;

    private FreeTradeNetwork.CatalogPayload payload;
    private int tab = TAB_HOW;
    private int page;
    private String query = "";
    private CatalogWidget.StockFilter filter = CatalogWidget.StockFilter.ALL;

    private CatalogWidget catalog;
    private EditBox search;
    private Button prev;
    private Button next;
    private final Button[] filterButtons = new Button[3];

    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;

    private int textPage = -1;
    private int textWidth = -1;
    private List<FormattedCharSequence> textLines = List.of();

    public GuideScreen(FreeTradeNetwork.CatalogPayload payload) {
        super(Component.translatable("freetrade.guide.title"));
        this.payload = payload;
    }

    /** Called when a fresh catalog arrives while this screen is open. */
    public void update(FreeTradeNetwork.CatalogPayload p) {
        this.payload = p;
        this.textPage = -1; // settings may have changed: rebuild the page text
        if (catalog != null) catalog.setEntries(p.entries());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    protected void init() {
        layoutPanel();
        if (catalog == null) catalog = new CatalogWidget(font, 0, 0, 0, 0, false);
        catalog.setEntries(payload.entries());
        catalog.setStockFilter(filter);

        int tabW = (panelW - 20) / 2;
        Button how = addRenderableWidget(Button.builder(Component.translatable("freetrade.guide.tab.how"),
                b -> switchTab(TAB_HOW)).bounds(panelX + 8, panelY + 6, tabW, 16).build());
        how.active = tab != TAB_HOW;
        Button cat = addRenderableWidget(Button.builder(Component.translatable("freetrade.guide.tab.catalog"),
                b -> switchTab(TAB_CATALOG)).bounds(panelX + 12 + tabW, panelY + 6, tabW, 16).build());
        cat.active = tab != TAB_CATALOG;

        if (tab == TAB_HOW) {
            prev = addRenderableWidget(Button.builder(Component.literal("<"), b -> turn(-1))
                    .bounds(panelX + 8, panelY + panelH - 22, 40, 16).build());
            next = addRenderableWidget(Button.builder(Component.literal(">"), b -> turn(1))
                    .bounds(panelX + panelW - 48, panelY + panelH - 22, 40, 16).build());
            updatePageButtons();
        } else {
            prev = null;
            next = null;
            initCatalogTab();
        }
    }

    private void initCatalogTab() {
        int w = panelW - 16;
        search = addRenderableWidget(new EditBox(font, panelX + 8, panelY + 26, w, 14,
                Component.translatable("freetrade.guide.search")));
        search.setHint(Component.translatable("freetrade.guide.search"));
        search.setValue(query);
        search.setResponder(s -> {
            query = s;
            catalog.setQuery(s);
        });

        int refreshW = 56;
        int fw = (w - refreshW - 6) / 3;
        CatalogWidget.StockFilter[] values = CatalogWidget.StockFilter.values();
        for (int i = 0; i < 3; i++) {
            CatalogWidget.StockFilter f = values[i];
            Component label = Component.translatable(switch (f) {
                case ALL -> "freetrade.guide.filter.all";
                case IN_STOCK -> "freetrade.guide.filter.in_stock";
                case OUT_OF_STOCK -> "freetrade.guide.filter.out_of_stock";
            });
            filterButtons[i] = addRenderableWidget(Button.builder(label, b -> setFilter(f))
                    .bounds(panelX + 8 + i * (fw + 2), panelY + 44, fw, 16).build());
            filterButtons[i].active = filter != f;
        }
        addRenderableWidget(Button.builder(Component.translatable("freetrade.guide.refresh"), b ->
                ClientPacketDistributor.sendToServer(new FreeTradeNetwork.CatalogRequest(true)))
                .bounds(panelX + panelW - 8 - refreshW, panelY + 44, refreshW, 16).build());

        catalog.setBounds(panelX + 8, panelY + 64, w, panelH - 64 - 20);
    }

    private void layoutPanel() {
        if (tab == TAB_CATALOG) {
            panelW = fit(360, width - 24, 300);
            panelH = fit(260, height - 24, 200);
        } else {
            panelW = fit(300, width - 16, 200);
            panelH = fit(200, height - 16, 160);
        }
        panelX = (width - panelW) / 2;
        panelY = (height - panelH) / 2;
    }

    private static int fit(int preferred, int available, int min) {
        return Math.max(Math.min(min, available), Math.min(preferred, available));
    }

    private void switchTab(int t) {
        tab = t;
        rebuildWidgets();
    }

    private void turn(int delta) {
        page = Math.max(0, Math.min(PAGES - 1, page + delta));
        updatePageButtons();
    }

    private void updatePageButtons() {
        if (prev != null) prev.active = page > 0;
        if (next != null) next.active = page < PAGES - 1;
    }

    private void setFilter(CatalogWidget.StockFilter f) {
        filter = f;
        catalog.setStockFilter(f);
        CatalogWidget.StockFilter[] values = CatalogWidget.StockFilter.values();
        for (int i = 0; i < 3; i++) {
            if (filterButtons[i] != null) filterButtons[i].active = values[i] != f;
        }
    }

    /** The background is drawn by the framework (extractBackground) before this; the panel goes under the widgets. */
    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        drawPanel(g);
        super.extractRenderState(g, mouseX, mouseY, partialTick);

        List<Component> tooltip = null;
        if (tab == TAB_HOW) {
            renderHow(g);
        } else if (Boolean.parseBoolean(payload.header().get("catalog_hidden"))) {
            // the server admin does not let regular players see the catalog in the book
            int y = panelY + panelH / 2 - 10;
            for (var line : font.split(Component.translatable("freetrade.guide.catalog_hidden"), panelW - 24)) {
                g.centeredText(font, line, panelX + panelW / 2, y, MUTED);
                y += 10;
            }
        } else {
            g.text(font, Component.translatable("freetrade.guide.count", catalog.visibleCount()),
                    panelX + 8, panelY + panelH - 14, MUTED, false);
            catalog.render(g, mouseX, mouseY);
            tooltip = catalog.tooltip(mouseX, mouseY);
        }
        if (tooltip != null) g.setComponentTooltipForNextFrame(font, tooltip, mouseX, mouseY);
    }

    private void drawPanel(GuiGraphicsExtractor g) {
        g.fill(panelX - 2, panelY - 2, panelX + panelW + 2, panelY + panelH + 2, BORDER);
        g.fill(panelX, panelY, panelX + panelW, panelY + panelH, PAPER);
        g.fill(panelX + 3, panelY + 3, panelX + panelW - 3, panelY + 4, BORDER);
        g.fill(panelX + 3, panelY + panelH - 4, panelX + panelW - 3, panelY + panelH - 3, BORDER);
    }

    private void renderHow(GuiGraphicsExtractor g) {
        int textW = panelW - 24;
        Component title = Component.translatable("freetrade.guide.page." + (page + 1) + ".title");
        g.centeredText(font, title, panelX + panelW / 2, panelY + 26, INK);

        List<Component> header = headerLines();
        List<FormattedCharSequence> headerRows = new ArrayList<>();
        for (Component c : header) headerRows.addAll(font.split(c, textW));
        int headerTop = panelY + panelH - 26 - headerRows.size() * 9;
        int y = panelY + 40;
        for (FormattedCharSequence line : bodyLines(textW)) {
            if (y + 9 > headerTop - 2) break;
            g.text(font, line, panelX + 12, y, INK, false);
            y += 9;
        }
        int hy = headerTop;
        for (FormattedCharSequence row : headerRows) {
            g.text(font, row, panelX + 12, hy, MUTED, false);
            hy += 9;
        }
        g.centeredText(font, Component.translatable("freetrade.guide.page_indicator", page + 1, PAGES),
                panelX + panelW / 2, panelY + panelH - 18, MUTED);
    }

    private List<FormattedCharSequence> bodyLines(int textW) {
        if (page == textPage && textWidth == textW) return textLines;
        List<FormattedCharSequence> out = new ArrayList<>();
        String body = Component.translatable("freetrade.guide.page." + (page + 1) + ".body", pageArgs(page)).getString();
        for (String paragraph : body.split("\n")) {
            if (paragraph.isBlank()) continue;
            out.addAll(font.split(Component.literal(paragraph), textW));
        }
        textPage = page;
        textWidth = textW;
        textLines = out;
        return out;
    }

    private List<Component> headerLines() {
        return List.of(Component.translatable("freetrade.guide.header", payload.header().get("database")));
    }

    private static Component yesNo(String v) {
        return Component.translatable(Boolean.parseBoolean(v) ? "freetrade.guide.yes" : "freetrade.guide.no");
    }

    /** Arguments of each page, always taken from the running server's settings (never the defaults). */
    private Object[] pageArgs(int p) {
        var h = payload.header();
        return switch (p) {
            case 0 -> {
                List<String> parts = new ArrayList<>();
                for (var d : dev.freetrade.currency.Currency.denominations()) {
                    parts.add(Component.translatable("freetrade.guide.money_item",
                            new net.minecraft.world.item.ItemStack(d.item()).getHoverName(), Money.format(d.milli())).getString());
                }
                yield new Object[]{String.join(", ", parts),
                        Component.translatable(dev.freetrade.net.ClientHooks.bankAvailable ? "freetrade.guide.on" : "freetrade.guide.off")};
            }
            case 1 -> new Object[]{h.get("formula"), h.get("reference"),
                    "-1".equals(h.get("ceiling")) ? Component.translatable("freetrade.guide.no_ceiling")
                            : Component.translatable("freetrade.guide.um", h.get("ceiling")),
                    h.get("min_unit")};
            case 2 -> new Object[]{h.get("labor"), yesNo(h.get("dynamic"))};
            case 3 -> new Object[]{h.get("villager_stacks"), yesNo(h.get("damaged"))};
            case 4, 5 -> new Object[]{h.get("machine_batch"), h.get("machine_interval")};
            case 6 -> new Object[]{h.get("tax_sell"), h.get("tax_buy"),
                    Boolean.parseBoolean(h.get("backoff_enabled"))
                            ? Component.translatable("freetrade.guide.backoff_on", h.get("backoff_threshold"),
                            h.get("backoff_window"), h.get("backoff_percent"), h.get("backoff_max"))
                            : Component.translatable("freetrade.guide.backoff_off"),
                    h.treasuryInfinite() ? Component.translatable("freetrade.guide.treasury_infinite")
                            : Component.translatable("freetrade.guide.treasury_finite", Money.format(h.treasury()))};
            default -> new Object[0];
        };
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (tab == TAB_CATALOG && catalog.mouseClicked(event.x(), event.y(), event.button())) return true;
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (tab == TAB_CATALOG && catalog.mouseScrolled(mouseX, mouseY, scrollY)) return true;
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }
}
