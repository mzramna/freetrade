package dev.freetrade.compat.emi;

import dev.emi.emi.api.EmiDragDropHandler;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.freetrade.client.MachineScreen;
import net.minecraft.world.item.ItemStack;

/** EMI integration: items dropped from the recipe viewer onto a machine's ghost box become its target. */
@EmiEntrypoint
public class FreeTradeEmiPlugin implements EmiPlugin {
    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void register(EmiRegistry registry) {
        registry.addDragDropHandler(MachineScreen.class, new EmiDragDropHandler<MachineScreen>() {
            @Override
            public boolean dropStack(MachineScreen screen, EmiIngredient dragged, int x, int y) {
                if (!screen.ghostArea().contains(x, y)) return false;
                for (EmiStack s : dragged.getEmiStacks()) {
                    ItemStack stack = s.getItemStack();
                    if (!stack.isEmpty()) {
                        screen.acceptGhost(stack);
                        return true;
                    }
                }
                return false;
            }
        });
    }
}
