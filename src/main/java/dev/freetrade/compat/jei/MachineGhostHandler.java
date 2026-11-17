package dev.freetrade.compat.jei;

import java.util.ArrayList;
import java.util.List;

import dev.freetrade.client.MachineScreen;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import net.minecraft.world.item.ItemStack;

/** Accepts item stacks dragged from JEI onto the machine's ghost box. */
@SuppressWarnings("rawtypes")
final class MachineGhostHandler implements IGhostIngredientHandler<MachineScreen> {
    @Override
    public <I> List<Target<I>> getTargetsTyped(MachineScreen gui, ITypedIngredient<I> ingredient, boolean doStart) {
        List<Target<I>> targets = new ArrayList<>();
        if (!(ingredient.getIngredient() instanceof ItemStack)) return targets;
        targets.add(new Target<I>() {
            @Override
            public net.minecraft.client.renderer.Rect2i getArea() {
                return gui.ghostArea();
            }

            @Override
            public void accept(I ing) {
                if (ing instanceof ItemStack stack) gui.acceptGhost(stack);
            }
        });
        return targets;
    }

    @Override
    public void onComplete() {
    }
}
