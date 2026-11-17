package dev.freetrade.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import javax.annotation.Nullable;

import dev.freetrade.market.Money;
import dev.freetrade.net.Catalog;
import dev.freetrade.util.ItemKeys;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Scrollable, searchable list of catalog entries (shared by the guide book and the representative's buy tab).
 * Each row: icon, name, stock, sell and buy price of one unit. Not a vanilla widget: the owning screen forwards
 * render / mouseClicked / mouseScrolled calls.
 */
public class CatalogWidget {
    public enum StockFilter { ALL, IN_STOCK, OUT_OF_STOCK }

    public static final int ROW_HEIGHT = 20;

    private final Font font;
    private int x, y, width, height;
    private final boolean selectable;
    private List<Catalog.Entry> all = List.of();
    private final List<Catalog.Entry> visible = new ArrayList<>();
    private String query = "";
    private StockFilter stockFilter = StockFilter.ALL;
    private int scroll;
    @Nullable private Catalog.Entry selected;
    private Consumer<Catalog.Entry> onSelect = e -> {
    };

    public CatalogWidget(Font font, int x, int y, int width, int height, boolean selectable) {
        this.font = font;
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        this.selectable = selectable;
    }

    public void setBounds(int x, int y, int width, int height) {
        this.x = x;
        this.y = y;
        this.width = width;
        this.height = height;
        clampScroll();
    }

    public void setEntries(List<Catalog.Entry> entries) {
        this.all = entries;
        if (selected != null) {
            String id = selected.item();
            selected = entries.stream().filter(e -> e.item().equals(id)).findFirst().orElse(null);
        }
        refilter();
    }

    public void setQuery(String q) {
        this.query = q == null ? "" : q.trim().toLowerCase(Locale.ROOT);
        scroll = 0;
        refilter();
    }

    public void setStockFilter(StockFilter f) {
        this.stockFilter = f;
        scroll = 0;
        refilter();
    }

    public StockFilter stockFilter() {
        return stockFilter;
    }

    public void setOnSelect(Consumer<Catalog.Entry> onSelect) {
        this.onSelect = onSelect;
    }

    @Nullable
    public Catalog.Entry selected() {
        return selected;
    }

    public int visibleCount() {
        return visible.size();
    }

    public static Component name(Catalog.Entry e) {
        ItemStack s = ItemKeys.stack(e.item(), 1);
        return s.isEmpty() ? Component.literal(e.item()) : s.getHoverName();
    }

    private void refilter() {
        visible.clear();
        for (Catalog.Entry e : all) {
            if (stockFilter == StockFilter.IN_STOCK && e.stock() <= 0) continue;
            if (stockFilter == StockFilter.OUT_OF_STOCK && e.stock() > 0) continue;
            if (!query.isEmpty()) {
                String n = name(e).getString().toLowerCase(Locale.ROOT);
                if (!n.contains(query) && !e.item().contains(query)) continue;
            }
            visible.add(e);
        }
        visible.sort((a, b) -> name(a).getString().compareToIgnoreCase(name(b).getString()));
        clampScroll();
    }

    private int rows() {
        return Math.max(1, height / ROW_HEIGHT);
    }

    private void clampScroll() {
        scroll = Math.max(0, Math.min(scroll, Math.max(0, visible.size() - rows())));
    }

    public static String price(long milli) {
        return milli < 0 ? "-" : Money.format(milli);
    }

    public void render(GuiGraphicsExtractor g, int mouseX, int mouseY) {
        g.fill(x, y, x + width, y + height, 0xC0101010);
        int rows = rows();
        for (int i = 0; i < rows && scroll + i < visible.size(); i++) {
            Catalog.Entry e = visible.get(scroll + i);
            int ry = y + i * ROW_HEIGHT;
            boolean hover = mouseX >= x && mouseX < x + width - 4 && mouseY >= ry && mouseY < ry + ROW_HEIGHT;
            if (e == selected) g.fill(x, ry, x + width - 4, ry + ROW_HEIGHT, 0x6040A040);
            else if (hover) g.fill(x, ry, x + width - 4, ry + ROW_HEIGHT, 0x30FFFFFF);
            g.item(ItemKeys.stack(e.item(), 1), x + 2, ry + 2);
            String n = font.plainSubstrByWidth(name(e).getString(), width - 26);
            g.text(font, n, x + 22, ry + 1, 0xFFFFFFFF, false);
            int stockColor = e.stock() > 0 ? 0xFF80FF80 : 0xFFFF8080;
            Component line = Component.translatable("freetrade.catalog.row", e.stock(), price(e.sell()), price(e.buy()));
            g.text(font, font.plainSubstrByWidth(line.getString(), width - 26), x + 22, ry + 11, stockColor, false);
        }
        // scrollbar
        if (visible.size() > rows) {
            int barH = Math.max(10, height * rows / visible.size());
            int barY = y + (height - barH) * scroll / Math.max(1, visible.size() - rows);
            g.fill(x + width - 3, y, x + width, y + height, 0x40FFFFFF);
            g.fill(x + width - 3, barY, x + width, barY + barH, 0xC0FFFFFF);
        }
        if (visible.isEmpty()) {
            g.centeredText(font, Component.translatable("freetrade.catalog.empty"), x + width / 2, y + height / 2 - 4, 0xFFAAAAAA);
        }
    }

    /** Tooltip lines for the hovered row (null when none). */
    @Nullable
    public List<Component> tooltip(int mouseX, int mouseY) {
        Catalog.Entry e = at(mouseX, mouseY);
        if (e == null) return null;
        List<Component> l = new ArrayList<>();
        l.add(name(e));
        l.add(Component.translatable("freetrade.catalog.stock", e.stock()));
        l.add(Component.translatable("freetrade.catalog.sell", price(e.sell())));
        l.add(e.buy() < 0 ? Component.translatable("freetrade.catalog.buy_none")
                : Component.translatable("freetrade.catalog.buy", price(e.buy())));
        if (e.lot() > 1) l.add(Component.translatable("freetrade.catalog.lot", e.lot()));
        l.add(Component.literal(e.item()).withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
        return l;
    }

    @Nullable
    private Catalog.Entry at(double mouseX, double mouseY) {
        if (mouseX < x || mouseX >= x + width - 4 || mouseY < y || mouseY >= y + height) return null;
        int i = scroll + (int) ((mouseY - y) / ROW_HEIGHT);
        return i >= 0 && i < visible.size() ? visible.get(i) : null;
    }

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!selectable || button != 0) return false;
        Catalog.Entry e = at(mouseX, mouseY);
        if (e == null) return false;
        selected = e;
        onSelect.accept(e);
        return true;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (mouseX < x || mouseX >= x + width || mouseY < y || mouseY >= y + height) return false;
        scroll -= (int) Math.signum(delta) * 3;
        clampScroll();
        return true;
    }
}
