package org.valkyrienskies.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.api.TransformType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(EntityLivingBase.class)
public class MixinEntityLivingBase {
    @Shadow
    protected int newPosRotationIncrements;

    private Entity clientShipAnchoredDismountEntity;
    private Vector3d clientShipAnchoredDismountPos;

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void suppressVanillaLerpDuringShipLocalMovement(CallbackInfo callbackInfo) {
        EntityLivingBase entity = (EntityLivingBase) (Object) this;
        if (EntityDraggable.isUsingShipLocalMovement(entity)) {
            this.newPosRotationIncrements = 0;
        }
    }

    /**
     * This mixin allows players to breathe underwater when they're in an air pocket.
     */
    @Inject(method = "canBreatheUnderwater", at = @At("HEAD"), cancellable = true)
    private void onPreCanBreatheUnderwater(CallbackInfoReturnable<Boolean> cir) {
        EntityLivingBase thisEntity = (EntityLivingBase) ((Object) this);
        IEntityShipDraggable entityShipDraggable = thisEntity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (entityShipDraggable == null || !entityShipDraggable.getInAirPocket()) return;

        cir.setReturnValue(true);
    }

    //---everything related to modded chairs goes here---
    /**
     * Captures the ship-aware dismount position before the client clears its riding entity.
     * This lets the return injection distinguish successful dismounts from no-op dismount attempts.
     */
    @Inject(method = "dismountRidingEntity", at = @At("HEAD"))
    private void captureClientShipAnchoredSeatDismount(CallbackInfo ci) {
        EntityLivingBase thisEntity = (EntityLivingBase) (Object) this;
        if (!thisEntity.world.isRemote) return;

        this.clearClientShipAnchoredDismount();

        Entity mountedEntity = thisEntity.getRidingEntity();
        if (mountedEntity == null) return;

        Vector3d dismountPos = this.getShipAnchoredDismountPos(mountedEntity);
        if (dismountPos == null) return;

        this.clientShipAnchoredDismountEntity = mountedEntity;
        this.clientShipAnchoredDismountPos = dismountPos;
    }

    /**
     * Applies the captured ship-aware dismount position after the client has actually stopped riding.
     * This keeps client-side dismount placement consistent with server-side ship-anchored seat handling.
     */
    @Inject(method = "dismountRidingEntity", at = @At("RETURN"))
    private void applyClientShipAnchoredSeatDismount(CallbackInfo ci) {
        EntityLivingBase thisEntity = (EntityLivingBase) (Object) this;
        if (!thisEntity.world.isRemote || this.clientShipAnchoredDismountPos == null) {
            this.clearClientShipAnchoredDismount();
        }
        else {
            if (thisEntity.getRidingEntity() != this.clientShipAnchoredDismountEntity) {
                this.applyShipAnchoredDismount(thisEntity, this.clientShipAnchoredDismountEntity, this.clientShipAnchoredDismountPos);
            }
            this.clearClientShipAnchoredDismount();
        }
    }

    /**
     * Dismounts riders from chair entities from other mods anchored to ships into a free
     * space supported by the same ship, transformed back into world space.
     */
    @Inject(method = "dismountEntity", at = @At("HEAD"), cancellable = true)
    private void dismountFromShipAnchoredSeat(Entity mountedEntity, CallbackInfo ci) {
        Vector3d dismountPos = this.getShipAnchoredDismountPos(mountedEntity);
        if (dismountPos == null) return;

        EntityLivingBase thisEntity = (EntityLivingBase) (Object) this;
        this.applyShipAnchoredDismount(thisEntity, mountedEntity, dismountPos);
        ci.cancel();
    }

    private void clearClientShipAnchoredDismount() {
        this.clientShipAnchoredDismountEntity = null;
        this.clientShipAnchoredDismountPos = null;
    }

    @Nullable
    private Vector3d getShipAnchoredDismountPos(@Nullable Entity mountedEntity) {
        if (mountedEntity == null) return null;

        IShipAnchoredMount anchoredMount = mountedEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        if (anchoredMount == null || (!anchoredMount.isAnchoredToShip() && !anchoredMount.tryAnchorMount(mountedEntity))) {
            return null;
        }

        BlockPos localAnchorBlock = anchoredMount.getLocalAnchorBlock();
        Optional<PhysicsObject> mountedShip = ValkyrienUtils.getPhysoManagingBlock(mountedEntity.world, localAnchorBlock);
        return mountedShip.map(physicsObject -> this.findNearbyDismountPos(mountedEntity, localAnchorBlock, physicsObject)).orElse(null);
    }

    /**
     * choose random position near block to dismount entity to
     */
    private Vector3d findNearbyDismountPos(@NotNull Entity mountedEntity, @NotNull BlockPos localAnchorBlock, @NotNull PhysicsObject mountedShip) {
        List<BlockPos> cardinalPositions = new ArrayList<>();
        List<BlockPos> diagonalPositions = new ArrayList<>();

        for (int offsetX = -1; offsetX <= 1; offsetX++) {
            for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                int horizontalDistance = Math.abs(offsetX) + Math.abs(offsetZ);
                if (horizontalDistance == 0) continue;

                List<BlockPos> positions = horizontalDistance == 1 ? cardinalPositions : diagonalPositions;
                for (int offsetY = -1; offsetY <= 1; offsetY++) {
                    BlockPos localDismountBlock = localAnchorBlock.add(offsetX, offsetY, offsetZ);
                    if (this.hasRoomForPlayerOnShip(mountedEntity, localDismountBlock, mountedShip)) {
                        positions.add(localDismountBlock);
                    }
                }
            }
        }

        List<BlockPos> preferredPositions = cardinalPositions.isEmpty() ? diagonalPositions : cardinalPositions;
        BlockPos localDismountBlock = preferredPositions.isEmpty()
                ? localAnchorBlock.up() : preferredPositions.get(mountedEntity.world.rand.nextInt(preferredPositions.size()));
        Vector3d dismountPos = new Vector3d(
                localDismountBlock.getX() + 0.5D,
                localDismountBlock.getY(),
                localDismountBlock.getZ() + 0.5D
        );
        mountedShip.getShipTransform().transformPosition(dismountPos, TransformType.SUBSPACE_TO_GLOBAL);
        return dismountPos;
    }

    private boolean hasRoomForPlayerOnShip(@NotNull Entity mountedEntity, @NotNull BlockPos localDismountBlock, @NotNull PhysicsObject mountedShip) {
        Optional<PhysicsObject> supportingShip = ValkyrienUtils.getPhysoManagingBlock(mountedEntity.world, localDismountBlock.down());
        return supportingShip.isPresent()
                && supportingShip.get().getUuid().equals(mountedShip.getUuid())
                && mountedEntity.world.getBlockState(localDismountBlock.down()).isTopSolid()
                && !mountedEntity.world.getBlockState(localDismountBlock).getMaterial().isSolid()
                && !mountedEntity.world.getBlockState(localDismountBlock.up()).getMaterial().isSolid();
    }

    private void applyShipAnchoredDismount(@NotNull EntityLivingBase thisEntity, @NotNull Entity mountedEntity, @NotNull Vector3d dismountPos) {
        thisEntity.motionX = 0D;
        thisEntity.motionY = 0D;
        thisEntity.motionZ = 0D;
        thisEntity.fallDistance = 0f;
        thisEntity.setPositionAndUpdate(dismountPos.x, dismountPos.y, dismountPos.z);
        thisEntity.prevPosX = thisEntity.lastTickPosX = dismountPos.x;
        thisEntity.prevPosY = thisEntity.lastTickPosY = dismountPos.y;
        thisEntity.prevPosZ = thisEntity.lastTickPosZ = dismountPos.z;

        IEntityShipDraggable draggable = thisEntity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable != null) {
            IShipAnchoredMount anchoredMount = mountedEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
            Optional<PhysicsObject> mountedShip = anchoredMount == null ?
                    Optional.empty() : ValkyrienUtils.getPhysoManagingBlock(mountedEntity.world, anchoredMount.getLocalAnchorBlock());

            draggable.setLastTouchedShip(mountedShip.map(PhysicsObject::getShipData).orElse(null));
            draggable.setTicksSinceTouchedShip(0);
            draggable.setTicksPartOfGround(0);
            draggable.setAddedLinearVelocity(new Vector3d());
            draggable.setAddedYawVelocity(0);
        }
    }
}
