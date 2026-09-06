package org.valkyrienskies.mixin.client.network;

import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.entity.Entity;
import net.minecraft.network.play.server.SPacketEntity;
import net.minecraft.network.play.server.SPacketEntityHeadLook;
import net.minecraft.network.play.server.SPacketEntityTeleport;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.ShipLocalEntityMovementData;

/**
 * Treats any ordinary entity movement packet as a return to vanilla world-space authority.
 */
@Mixin(NetHandlerPlayClient.class)
public class MixinNetHandlerPlayClient {
    @Shadow
    private WorldClient world;

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
