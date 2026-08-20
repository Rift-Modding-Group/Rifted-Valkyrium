package org.valkyrienskies.addon.control.jei;

import jakarta.annotation.Nullable;
import mezz.jei.api.IJeiRuntime;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.IModRegistry;
import mezz.jei.api.JEIPlugin;
import mezz.jei.api.gui.IAdvancedGuiHandler;
import mezz.jei.api.ingredients.IIngredientBlacklist;
import net.minecraft.item.ItemStack;
import org.jspecify.annotations.NonNull;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;
import org.valkyrienskies.addon.control.gui.GuiPhysicsInfuser;

import java.awt.*;
import java.util.Collections;
import java.util.List;

@JEIPlugin
public class VSControlJEI implements IModPlugin {
    @Override
    public void register(IModRegistry registry) {
        //hide dummy blocks
        IIngredientBlacklist blacklist = registry.getJeiHelpers().getIngredientBlacklist();
        blacklist.addIngredientToBlacklist(new ItemStack(ValkyrienSkiesControl.INSTANCE.vsControlBlocks.dummyTelegraph));
        blacklist.addIngredientToBlacklist(new ItemStack(ValkyrienSkiesControl.INSTANCE.vsControlBlocks.dummyRenderBlock));
        blacklist.addIngredientToBlacklist(new ItemStack(ValkyrienSkiesControl.INSTANCE.vsControlBlocks.physicsInfuserDummy));

        //for the physics infuser gui
        registry.addAdvancedGuiHandlers(new PhysicsInfuserGuiHandler());
    }

    private static class PhysicsInfuserGuiHandler implements IAdvancedGuiHandler<GuiPhysicsInfuser> {
        @Override
        public Class<GuiPhysicsInfuser> getGuiContainerClass() {
            return GuiPhysicsInfuser.class;
        }

        @Nullable
        @Override
        public List<Rectangle> getGuiExtraAreas(GuiPhysicsInfuser gui) {
            int x = (gui.width / 2) + 90;
            int y = (gui.height / 2) - 70;
            Rectangle buttonArea = new Rectangle(x, y, 98, 70);

            return Collections.singletonList(buttonArea);
        }

        @Nullable
        @Override
        public Object getIngredientUnderMouse(@NonNull GuiPhysicsInfuser gui, int mouseX, int mouseY) {
            return null;
        }
    }
}
