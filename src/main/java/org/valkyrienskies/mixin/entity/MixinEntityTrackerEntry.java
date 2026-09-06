package org.valkyrienskies.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityTracker;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.SPacketEntity;
import net.minecraft.network.play.server.SPacketEntityHeadLook;
import net.minecraft.network.play.server.SPacketEntityTeleport;
import net.minecraft.network.play.server.SPacketEntityVelocity;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.ShipLocalEntityMovementData;
import org.valkyrienskies.mod.common.network.MessageEntityShipMovement;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;

import java.util.List;
import java.util.Set;
import java.util.UUID;

@Mixin(EntityTrackerEntry.class)
public class MixinEntityTrackerEntry {
    @Shadow
    @Final
    private Entity trackedEntity;

    @Shadow
    @Final
    private Set<EntityPlayerMP> trackingPlayers;

    @Shadow
    private long encodedPosX;

    @Shadow
    private long encodedPosY;

    @Shadow
    private long encodedPosZ;

    @Shadow
    private int ticksSinceLastForcedTeleport;

    @Shadow
    public int updateCounter;

    @Unique
    private boolean updatingPlayerList;

    @Unique
    private int shipMovementComponentMask;

    @Unique
    @Nullable
    private ShipData activeMovementShip;

    @Unique
    @Nullable
    private UUID lastMovementShipUuid;

    @Unique
    private boolean playerWasAlreadyTracking;

    @Inject(method = "updatePlayerList", at = @At("HEAD"))
    private void beginShipMovementPacketBatch(List<EntityPlayer> players, CallbackInfo callbackInfo) {
        this.updatingPlayerList = true;
        this.shipMovementComponentMask = 0;
        this.activeMovementShip = EntityDraggable.getActiveShip(this.trackedEntity);

        if (this.activeMovementShip == null) {
            if (this.lastMovementShipUuid != null) {
                this.sendShipMovementMessage(new MessageEntityShipMovement(this.trackedEntity.getEntityId()));
                this.lastMovementShipUuid = null;

                // Ship-local velocity will no longer be applied after this update. Refresh vanilla's velocity first so
                // projectiles and other simulated entities resume world-space movement from the server's current state.
                SPacketEntityVelocity velocityPacket = new SPacketEntityVelocity(this.trackedEntity);
                for (EntityPlayerMP trackingPlayer : this.trackingPlayers) {
                    trackingPlayer.connection.sendPacket(velocityPacket);
                }

                // Make vanilla follow the stop marker with an authoritative world-space target in this same update.
                // This prevents a partially completed ship-local lerp from becoming the entity's resting position.
                this.ticksSinceLastForcedTeleport = 401;
                this.trackedEntity.isAirBorne = true;
            }
            return;
        }

        UUID activeShipUuid = this.activeMovementShip.getUuid();
        if (!activeShipUuid.equals(this.lastMovementShipUuid)) {
            this.shipMovementComponentMask = MessageEntityShipMovement.ALL_COMPONENTS;
        }
        else if (this.updateCounter % ShipLocalEntityMovementData.DEFAULT_LERP_STEPS == 0) {
            // Vanilla lets several entity types go 10-20 ticks between updates. Refresh every authoritative component
            // before its three-step interpolation expires, including projectile rotation and velocity.
            this.shipMovementComponentMask = MessageEntityShipMovement.ALL_COMPONENTS;
        }
    }

    @Inject(method = "updatePlayerList", at = @At("RETURN"))
    private void finishShipMovementPacketBatch(List<EntityPlayer> players, CallbackInfo callbackInfo) {
        if (this.activeMovementShip != null && this.shipMovementComponentMask != 0) {
            this.sendShipMovementMessage(new MessageEntityShipMovement(
                    this.trackedEntity,
                    this.activeMovementShip,
                    this.shipMovementComponentMask,
                    this.encodedPosX,
                    this.encodedPosY,
                    this.encodedPosZ
            ));
            this.lastMovementShipUuid = this.activeMovementShip.getUuid();
        }

        this.updatingPlayerList = false;
        this.shipMovementComponentMask = 0;
        this.activeMovementShip = null;
    }

    @Inject(method = "sendPacketToTrackedPlayers", at = @At("HEAD"), cancellable = true)
    private void replaceVanillaMovementPacket(Packet<?> packet, CallbackInfo callbackInfo) {
        int componentMask;
        switch (packet) {
            case SPacketEntity.S17PacketEntityLookMove s17PacketEntityLookMove ->
                    componentMask = MessageEntityShipMovement.POSITION | MessageEntityShipMovement.ROTATION;
            case SPacketEntity.S15PacketEntityRelMove s15PacketEntityRelMove ->
                    componentMask = MessageEntityShipMovement.POSITION;
            case SPacketEntity.S16PacketEntityLook s16PacketEntityLook ->
                    componentMask = MessageEntityShipMovement.ROTATION;
            case SPacketEntityTeleport sPacketEntityTeleport ->
                    componentMask = MessageEntityShipMovement.POSITION | MessageEntityShipMovement.ROTATION;
            case SPacketEntityVelocity sPacketEntityVelocity -> componentMask = MessageEntityShipMovement.VELOCITY;
            case SPacketEntityHeadLook sPacketEntityHeadLook -> componentMask = MessageEntityShipMovement.HEAD_ROTATION;
            case null, default -> {
                return;
            }
        }

        ShipData activeShip = this.updatingPlayerList ? this.activeMovementShip : EntityDraggable.getActiveShip(this.trackedEntity);
        if (activeShip == null) return;

        if (this.updatingPlayerList) {
            this.shipMovementComponentMask |= componentMask;
        }
        else {
            this.sendShipMovementMessage(new MessageEntityShipMovement(
                    this.trackedEntity,
                    activeShip,
                    componentMask,
                    this.encodedPosX,
                    this.encodedPosY,
                    this.encodedPosZ
            ));
            this.lastMovementShipUuid = activeShip.getUuid();
        }
        callbackInfo.cancel();
    }

    @Inject(method = "updatePlayerEntity", at = @At("RETURN"))
    private void initializeNewTrackingPlayer(EntityPlayerMP player, CallbackInfo callbackInfo) {
        if (this.playerWasAlreadyTracking
                || !this.trackingPlayers.contains(player)
                || this.shipMovementComponentMask == MessageEntityShipMovement.ALL_COMPONENTS) {
            return;
        }

        ShipData activeShip = EntityDraggable.getActiveShip(this.trackedEntity);
        if (activeShip == null) return;

        ValkyrienSkiesMod.physWrapperNetwork.sendTo(new MessageEntityShipMovement(
                this.trackedEntity,
                activeShip,
                MessageEntityShipMovement.ALL_COMPONENTS,
                EntityTracker.getPositionLong(this.trackedEntity.posX),
                EntityTracker.getPositionLong(this.trackedEntity.posY),
                EntityTracker.getPositionLong(this.trackedEntity.posZ)
        ), player);
    }

    @Inject(method = "updatePlayerEntity", at = @At("HEAD"))
    private void rememberWhetherPlayerWasAlreadyTracking(EntityPlayerMP player, CallbackInfo callbackInfo) {
        this.playerWasAlreadyTracking = this.trackingPlayers.contains(player);
    }

    @Unique
    private void sendShipMovementMessage(MessageEntityShipMovement message) {
        for (EntityPlayerMP trackingPlayer : this.trackingPlayers) {
            ValkyrienSkiesMod.physWrapperNetwork.sendTo(message, trackingPlayer);
        }
    }

    //---rustic stuff coz fuck rustic---
    //this is to make rustic chairs not autodismount the player when ship is going too fast and they sittin for a good while

    @Inject(method = "isVisibleTo", at = @At("HEAD"), cancellable = true)
    private void keepShipAnchoredMountVisibleToPassenger(EntityPlayerMP player, CallbackInfoReturnable<Boolean> callbackInfo) {
        IShipAnchoredMount anchoredMount = this.trackedEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        if (this.isRusticChairEntity(this.trackedEntity) && this.trackedEntity.getPassengers().isEmpty()
            && anchoredMount != null && (anchoredMount.isAnchoredToShip()
            || anchoredMount.tryAnchorMount(this.trackedEntity))
        ) {
            // Rustic kills a client chair that ticks without a passenger and then asks the server
            // to dismount. Wait until startRiding has populated the passenger list before spawning it.
            callbackInfo.setReturnValue(false);
            return;
        }

        if (this.isShipAnchoredPassenger(player)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "isPlayerWatchingThisChunk", at = @At("HEAD"), cancellable = true)
    private void keepShipAnchoredMountTrackedByPassenger(EntityPlayerMP player, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (this.isShipAnchoredPassenger(player)) {
            callbackInfo.setReturnValue(true);
        }
    }

    private boolean isRusticChairEntity(@NotNull Entity entity) {
        ResourceLocation entityId = EntityList.getKey(entity);
        return entityId != null && "rustic".equals(entityId.getNamespace()) && entityId.getPath().equals("chair");
    }

    private boolean isShipAnchoredPassenger(EntityPlayerMP player) {
        if (!this.trackedEntity.isPassenger(player)) return false;

        IShipAnchoredMount anchoredMount = this.trackedEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        return anchoredMount != null && (anchoredMount.isAnchoredToShip() || anchoredMount.tryAnchorMount(this.trackedEntity));
    }
}
