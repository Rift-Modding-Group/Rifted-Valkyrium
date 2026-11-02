package org.valkyrienskies.mixin.network.play.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.INetHandlerPlayServer;
import net.minecraft.network.play.client.CPacketVehicleMove;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.network.VehicleMovementData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;

/**
 * Keeps vehicle movement in the same ship frame on both sides without changing ordinary vehicles.
 * Is part of an initiative to prevent "(Entity) moves wrongly" spam in log when riding said entity.
 */
@Mixin(CPacketVehicleMove.class)
public class MixinCPacketVehicleMove {
    @Unique
    @Nullable
    private VehicleMovementData movementData;
    @Unique
    @Nullable
    private VehicleMovementData.WorldMovement resolvedMovement;

    @Inject(method = "<init>(Lnet/minecraft/entity/Entity;)V", at = @At("RETURN"))
    private void captureShipMovement(Entity vehicle, CallbackInfo callbackInfo) {
        ShipData ship = EntityDraggable.getActiveShip(vehicle);
        if (ship == null) return;

        this.movementData = VehicleMovementData.fromWorld(
                ship.getUuid(), ship.getShipTransform(),
                new Vector3d(vehicle.posX, vehicle.posY, vehicle.posZ), vehicle.rotationYaw
        );
    }

    @Inject(method = "writePacketData", at = @At("TAIL"))
    private void writeShipMovement(PacketBuffer buffer, CallbackInfo callbackInfo) {
        VehicleMovementData.write(buffer, this.movementData);
    }

    @Inject(method = "readPacketData", at = @At("TAIL"))
    private void readShipMovement(PacketBuffer buffer, CallbackInfo callbackInfo) {
        this.movementData = VehicleMovementData.read(buffer);
        this.resolvedMovement = null;
    }

    @WrapOperation(
            method = "processPacket(Lnet/minecraft/network/play/INetHandlerPlayServer;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/play/INetHandlerPlayServer;processVehicleMove(Lnet/minecraft/network/play/client/CPacketVehicleMove;)V")
    )
    private void resolveShipMovement(INetHandlerPlayServer handler, CPacketVehicleMove packet, Operation<Void> original) {
        // Vanilla first reschedules this packet onto the game thread. Resolve only on that invocation.
        if (handler instanceof NetHandlerPlayServer serverHandler
                && serverHandler.player.getServerWorld().isCallingFromMinecraftThread()
                && this.movementData != null
        ) {
            Entity vehicle = serverHandler.player.getLowestRidingEntity();
            ShipData ship = EntityDraggable.getActiveShip(vehicle);
            if (vehicle != serverHandler.player && vehicle.getControllingPassenger() == serverHandler.player
                    && ship != null && ship.getUuid().equals(this.movementData.shipId())
            ) {
                this.resolvedMovement = VehicleMovementData.toWorld(this.movementData, ship.getShipTransform());
            }
        }
        original.call(handler, packet);
    }

    @Inject(method = "getX", at = @At("HEAD"), cancellable = true)
    private void getShipAdjustedX(CallbackInfoReturnable<Double> callbackInfo) {
        if (this.resolvedMovement != null) callbackInfo.setReturnValue(this.resolvedMovement.position().x());
    }

    @Inject(method = "getY", at = @At("HEAD"), cancellable = true)
    private void getShipAdjustedY(CallbackInfoReturnable<Double> callbackInfo) {
        if (this.resolvedMovement != null) callbackInfo.setReturnValue(this.resolvedMovement.position().y());
    }

    @Inject(method = "getZ", at = @At("HEAD"), cancellable = true)
    private void getShipAdjustedZ(CallbackInfoReturnable<Double> callbackInfo) {
        if (this.resolvedMovement != null) callbackInfo.setReturnValue(this.resolvedMovement.position().z());
    }

    @Inject(method = "getYaw", at = @At("HEAD"), cancellable = true)
    private void getShipAdjustedYaw(CallbackInfoReturnable<Float> callbackInfo) {
        if (this.resolvedMovement != null) callbackInfo.setReturnValue(this.resolvedMovement.yaw());
    }
}
