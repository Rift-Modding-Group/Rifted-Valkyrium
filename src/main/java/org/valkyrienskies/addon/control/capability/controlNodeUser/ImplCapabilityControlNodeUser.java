package org.valkyrienskies.addon.control.capability.controlNodeUser;

import net.minecraft.client.Minecraft;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.common.util.Constants;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;
import org.valkyrienskies.addon.control.network.VSNodeControlMessage;
import org.valkyrienskies.addon.control.nodeControls.NodeControl;
import org.valkyrienskies.addon.control.tileentity.TileEntityControlNodeImpl;
import org.valkyrienskies.addon.control.tileentity.TileEntityLiftLever;
import org.valkyrienskies.addon.control.tileentity.TileEntityShipHelm;
import org.valkyrienskies.addon.control.tileentity.TileEntitySpeedTelegraph;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.piloting.PilotControlsMessage;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.UUID;

public class ImplCapabilityControlNodeUser implements ICapabilityControlNodeUser {
    private PhysicsObject ship;
    private BlockPos usedControlNodePos;

    public PhysicsObject getShip() {
        return this.ship;
    }

    public void setShip(PhysicsObject physicsObject) {
        this.ship = physicsObject;
    }

    @Override
    public BlockPos getUsedControlNodePos() {
        return this.usedControlNodePos;
    }

    @Override
    public void setUsedControlNodePos(BlockPos pos) {
        this.usedControlNodePos = pos;
    }

    @Override
    public void stopUsingEverything() {
        this.setShip(null);
        this.setUsedControlNodePos(null);
    }

    @Override
    public void onClientTick() {
        if (this.usedControlNodePos != null) {
            ValkyrienSkiesControl.controlNodeNetwork.sendToServer(new VSNodeControlMessage(this.getUsedControlNodePos(), this.getControls()));
        }
    }

    private int getControls() {
        if (this.usedControlNodePos == null) return 0;
        TileEntity tileEntity = Minecraft.getMinecraft().world.getTileEntity(this.usedControlNodePos);
        if (!(tileEntity instanceof TileEntityControlNodeImpl teControlNode)) return 0;

        return switch (teControlNode) {
            case TileEntityShipHelm tileEntityShipHelm -> NodeControl.HELM_CONTROLS.getControls();
            case TileEntityLiftLever tileEntityLiftLever -> NodeControl.LIFT_LEVER_CONTROLS.getControls();
            case TileEntitySpeedTelegraph tileEntitySpeedTelegraph -> NodeControl.SPEED_TELEGRAPH_CONTROLS.getControls();
            default -> 0;
        };
    }
}
