package org.valkyrienskies.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mixin(EntityLivingBase.class)
public class MixinEntityLivingBase {
    private Entity clientShipAnchoredDismountEntity;
    private Vector3d clientShipAnchoredDismountPos;

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

        Vector3d dismountPos = this.getShipAnchoredBlockAboveDismountPos(mountedEntity);
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
     * Dismounts riders from chair entities from other mods anchored to ships onto the
     * ship-local block immediately above the seat, transformed back into world space.
     */
    @Inject(method = "dismountEntity", at = @At("HEAD"), cancellable = true)
    private void dismountFromShipAnchoredSeat(Entity mountedEntity, CallbackInfo ci) {
        Vector3d dismountPos = this.getShipAnchoredBlockAboveDismountPos(mountedEntity);
        if (dismountPos == null) return;

        EntityLivingBase thisEntity = (EntityLivingBase) (Object) this;
        this.applyShipAnchoredDismount(thisEntity, mountedEntity, dismountPos);
        ci.cancel();
    }

    private void clearClientShipAnchoredDismount() {
        this.clientShipAnchoredDismountEntity = null;
        this.clientShipAnchoredDismountPos = null;
    }

    /**
     * When dismounting a chair on a modded ship, it will ideally teleport them to the top of the chair
     * */
    private Vector3d getShipAnchoredBlockAboveDismountPos(Entity mountedEntity) {
        if (mountedEntity == null) return null;

        IShipAnchoredMount anchoredMount = mountedEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        if (anchoredMount == null || (!anchoredMount.isAnchoredToShip() && !anchoredMount.tryAnchorMount(mountedEntity))) {
            return null;
        }

        BlockPos localAnchorBlock = anchoredMount.getLocalAnchorBlock();
        Optional<PhysicsObject> mountedShip = ValkyrienUtils.getPhysoManagingBlock(mountedEntity.world, localAnchorBlock);
        if (mountedShip.isEmpty()) return null;

        EntityLivingBase thisEntity = (EntityLivingBase) (Object) this;
        Vector3d dismountPos = new Vector3d(
                localAnchorBlock.getX() + 0.5D,
                this.getDismountSupportY(thisEntity, mountedEntity, localAnchorBlock),
                localAnchorBlock.getZ() + 0.5D
        );
        mountedShip.get().getShipTransform().transformPosition(dismountPos, TransformType.SUBSPACE_TO_GLOBAL);
        return dismountPos;
    }

    /**
     * le ideal y position to teleport players dismounting chairs to
     * */
    private double getDismountSupportY(@NotNull EntityLivingBase rider, @NotNull Entity mountedEntity, @NotNull BlockPos localAnchorBlock) {
        double halfWidth = rider.width * 0.5D;
        double centerX = localAnchorBlock.getX() + 0.5D;
        double centerZ = localAnchorBlock.getZ() + 0.5D;
        AxisAlignedBB riderColumn = new AxisAlignedBB(
                centerX - halfWidth,
                localAnchorBlock.getY() - 1D,
                centerZ - halfWidth,
                centerX + halfWidth,
                localAnchorBlock.getY() + 4D,
                centerZ + halfWidth
        );
        List<AxisAlignedBB> collisionBoxes = new ArrayList<>();

        try {
            mountedEntity.world.getBlockState(localAnchorBlock).addCollisionBoxToList(
                    mountedEntity.world, localAnchorBlock, riderColumn,
                    collisionBoxes, rider, false
            );
        }
        catch (Throwable ignored) {
            collisionBoxes.clear();
        }

        double supportY = 0D;
        boolean hasSupport = false;
        for (AxisAlignedBB collisionBox : collisionBoxes) {
            if (collisionBox.maxX <= riderColumn.minX || collisionBox.minX >= riderColumn.maxX
                    || collisionBox.maxZ <= riderColumn.minZ || collisionBox.minZ >= riderColumn.maxZ
            ) {
                continue;
            }
            if (!hasSupport || collisionBox.maxY > supportY) {
                supportY = collisionBox.maxY;
                hasSupport = true;
            }
        }

        return hasSupport ? supportY + 0.001D : localAnchorBlock.getY() + 1D;
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

        IShipAnchoredMount anchoredMount = mountedEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        Optional<PhysicsObject> mountedShip = anchoredMount == null
                ? Optional.empty() : ValkyrienUtils.getPhysoManagingBlock(mountedEntity.world, anchoredMount.getLocalAnchorBlock());
        IEntityShipDraggable draggable = thisEntity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable != null) {
            draggable.setLastTouchedShip(mountedShip.map(PhysicsObject::getShipData).orElse(null));
            draggable.setTicksSinceTouchedShip(0);
            draggable.setTicksPartOfGround(0);
            draggable.setStandingOnShip(mountedShip.isPresent());
        }
    }
}
