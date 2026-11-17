package dev.freetrade.client;

import dev.freetrade.menu.TraderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** STUB — replaced by agent B. */
public class TraderScreen extends AbstractContainerScreen<TraderMenu> {
    public TraderScreen(TraderMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
    }
}
