package org.valkyrienskies.mixin.client.network;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.play.server.SPacketEntity;
import net.minecraft.network.play.server.SPacketEntityHeadLook;
import net.minecraft.network.play.server.SPacketEntityTeleport;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.ShipLocalEntityMovementData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.UUID;

/**
 * Treats any ordinary entity movement packet as a return to vanilla world-space authority.
 */
@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {
    @Shadow
    private WorldClient world;

    /**
     * helps out in handling player respawn on bed in ship upon death
     * */
    @Redirect(
            method = "handlePlayerPosLook",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayer;setPositionAndRotation(DDDFF)V")
    )
    private void placeRespawnInClientShipFrame(@NotNull EntityPlayer player, double x, double y, double z, float yaw, float pitch) {
        IEntityShipDraggable draggable = player.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable == null) {
            player.setPositionAndRotation(x, y, z, yaw, pitch); //cant predict what can happen w/out this shit
            return;
        }

        //for pegging to ship upon respawning on ship
        if (draggable.isPendingShipTeleport()) {
            UUID shipId = draggable.getPendingShipId();
            Vector3dc localPosition = draggable.getPendingShipLocalPosition();
            IPhysObjectWorld shipWorld = ValkyrienUtils.getPhysObjWorld(player.world);
            PhysicsObject respawnShip = shipId == null || shipWorld == null ? null : shipWorld.getPhysObjectFromUUID(shipId);

            //ship to respawn to located, tp there
            if (respawnShip != null && localPosition != null) {
                Vector3d position = new Vector3d(localPosition);
                respawnShip.getShipTransformationManager().getCurrentTickTransform().transformPosition(position, TransformType.SUBSPACE_TO_GLOBAL);
                player.setLocationAndAngles(position.x, position.y, position.z, yaw, pitch);
                EntityDraggable.startTrackingShip(player, respawnShip.getShipData());
                draggable.clearPendingShipPosition();
            }
            //no ship, use standard procedure
            else player.setPositionAndRotation(x, y, z, yaw, pitch);
        }
        //no pending tp flag, standard procedure
        else {
            draggable.clearPendingShipPosition();
            player.setPositionAndRotation(x, y, z, yaw, pitch);
        }
    }

    @Inject(method = "handleEntityMovement", at = @At("RETURN"))
    private void clearShipMovementAfterVanillaMove(SPacketEntity packet, CallbackInfo callbackInfo) {
        this.clearShipMovement(packet.getEntity(this.world));
    }

    @Inject(method = "handleEntityTeleport", at = @At("RETURN"))
    private void clearShipMovementAfterVanillaTeleport(SPacketEntityTeleport packet, CallbackInfo callbackInfo) {
        this.clearShipMovement(this.world.getEntityByID(packet.getEntityId()));
    }

    @Inject(method = "handleEntityVelocity", at = @At("RETURN"))
    private void clearShipMovementAfterVanillaVelocity(SPacketEntityVelocity packet, CallbackInfo callbackInfo) {
        this.clearShipMovement(this.world.getEntityByID(packet.getEntityID()));
    }

    @Inject(method = "handleEntityHeadLook", at = @At("RETURN"))
    private void clearShipMovementAfterVanillaHeadLook(SPacketEntityHeadLook packet, CallbackInfo callbackInfo) {
        this.clearShipMovement(packet.getEntity(this.world));
    }

    private void clearShipMovement(Entity entity) {
        if (entity == null) return;

        IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable == null) return;

        ShipLocalEntityMovementData movementData = draggable.getShipLocalMovementData();
        if (movementData != null) movementData.clear();
    }
}
