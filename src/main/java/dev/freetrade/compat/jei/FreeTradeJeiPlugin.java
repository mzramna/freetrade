package dev.freetrade.compat.jei;

import dev.freetrade.client.MachineScreen;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import net.minecraft.resources.Identifier;

/** JEI integration: items dragged from the recipe viewer can be dropped on a machine's ghost slot. */
@JeiPlugin
public class FreeTradeJeiPlugin implements IModPlugin {
    private static final Identifier ID = Identifier.fromNamespaceAndPath("freetrade", "jei");

    @Override
    public Identifier getPluginUid() {
        return ID;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        registration.addGhostIngredientHandler(MachineScreen.class, new MachineGhostHandler());
    }
}
