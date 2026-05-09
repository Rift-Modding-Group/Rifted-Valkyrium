package org.valkyrienskies.addon.control.tileentity;

import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.valkyrienskies.addon.control.network.VSNodeControlMessage;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

/**
 * This is where all the nodes that allow for controlling individual parts of
 * the ship are now put in. That means the helm, speed telegraph, and lift
 * lever
 * */
public interface ITileEntityControlNode {
    void onNodeControlsMessage(VSNodeControlMessage message, EntityPlayerMP sender);

    EntityPlayer getUserEntity();

    void setUserEntity(EntityPlayer newPilot);

    void playerWantsToStopUsing(EntityPlayer player);

    PhysicsObject getParentPhysicsEntity();

    //default void onStartTileUsage() {}

    //default void onStopTileUsage() {}

    /**
     * This is called during the post render of every frame in Minecraft. Override this to allow a
     * pilotable node tileentity to display info as text on the screen.
     *
     * @param renderer
     * @param gameResolution
     */
    @SideOnly(Side.CLIENT)
    default void renderPilotText(FontRenderer renderer, ScaledResolution gameResolution) {

    }
}
