package org.valkyrienskies.addon.control.tileentity;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumFacing;
import org.joml.Vector3d;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;
import org.valkyrienskies.addon.control.network.VSNodeControlMessage;
import org.valkyrienskies.addon.control.network.VSStartUsingControlNodeMessage;
import org.valkyrienskies.addon.control.network.VSStopUsingControlNodeMessage;
import org.valkyrienskies.addon.control.nodenetwork.BasicNodeTileEntity;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import javax.annotation.Nullable;
import java.util.UUID;

public abstract class TileEntityControlNodeImpl extends BasicNodeTileEntity implements ITileEntityControlNode {
    @Nullable
    private UUID pilotPlayerEntity;

    public TileEntityControlNodeImpl() {
        super();
        this.pilotPlayerEntity = null;
    }

    @Override
    public abstract void onNodeControlsMessage(VSNodeControlMessage message, EntityPlayerMP sender);

    @Override
    public EntityPlayer getUserEntity() {
        if (this.pilotPlayerEntity != null) {
            return this.world.getPlayerEntityByUUID(this.pilotPlayerEntity);
        }
        return null;
    }

    @Override
    public void setUserEntity(EntityPlayer newPilot) {
        if (!this.world.isRemote) {
            EntityPlayer oldPlayer = this.getUserEntity();
            this.sendPilotUpdatePackets((EntityPlayerMP) newPilot, (EntityPlayerMP) oldPlayer);
        }

        if (newPilot != null) {
            this.pilotPlayerEntity = newPilot.getUniqueID();
            //this.onStartTileUsage();
        }
        else {
            this.pilotPlayerEntity = null;
            //this.onStopTileUsage();
        }
    }

    @Override
    public void playerWantsToStopUsing(EntityPlayer player) {
        if (player == this.getUserEntity()) {
            this.setUserEntity(null);
        }
    }

    @Override
    public PhysicsObject getParentPhysicsEntity() {
        return ValkyrienUtils.getPhysoManagingBlock(this.world, this.pos).orElse(null);
    }

    /**
     * @param player
     * @param blockFacing
     * @return true if the passed player is in front of the given blockFacing, false if not.
     */
    protected boolean isPlayerInFront(EntityPlayer player, EnumFacing blockFacing) {
        Vector3d tileRelativePos = new Vector3d(this.getPos().getX() + .5, this.getPos().getY() + .5,
                this.getPos().getZ() + .5);
        if (this.getParentPhysicsEntity() != null) {
            this.getParentPhysicsEntity().getShipTransformationManager()
                    .getCurrentTickTransform()
                    .transformPosition(tileRelativePos, TransformType.SUBSPACE_TO_GLOBAL);
        }
        tileRelativePos.sub(player.posX, player.posY, player.posZ);
        Vector3d normal = new Vector3d(blockFacing.getDirectionVec().getX() * -1,
                blockFacing.getDirectionVec().getY(),
                blockFacing.getDirectionVec().getZ());

        if (this.getParentPhysicsEntity() != null) {
            this.getParentPhysicsEntity().getShipTransformationManager()
                    .getCurrentTickTransform()
                    .transformDirection(normal, TransformType.SUBSPACE_TO_GLOBAL);
        }

        double dotProduct = tileRelativePos.dot(normal);
        return dotProduct > 0;
    }

    private void sendPilotUpdatePackets(EntityPlayerMP newUser, EntityPlayerMP oldUser) {
        // If old user equals new user, then don't send the stop use message
        if (oldUser != null && oldUser != newUser) {
            VSStopUsingControlNodeMessage stopMessage = new VSStopUsingControlNodeMessage(getPos());
            ValkyrienSkiesControl.controlNodeNetwork.sendTo(stopMessage, oldUser);
        }
        if (newUser != null) {
            VSStartUsingControlNodeMessage startMessage = new VSStartUsingControlNodeMessage(this.getPos());
            ValkyrienSkiesControl.controlNodeNetwork.sendTo(startMessage, newUser);
        }
    }
}
