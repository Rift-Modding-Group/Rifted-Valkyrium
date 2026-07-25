package org.valkyrienskies.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.entity.EntityShipMovementData;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

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

        Vector3d dismountPos = getShipAnchoredBlockAboveDismountPos(mountedEntity);
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
                this.applyShipAnchoredDismount(
                        thisEntity,
                        this.clientShipAnchoredDismountEntity,
                        this.clientShipAnchoredDismountPos
                );
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

        Vector3d dismountPos = new Vector3d(
                localAnchorBlock.getX() + 0.5D,
                this.getBlockAboveDismountY(mountedEntity, localAnchorBlock),
                localAnchorBlock.getZ() + 0.5D
        );
        mountedShip.get().getShipTransform().transformPosition(dismountPos, TransformType.SUBSPACE_TO_GLOBAL);
        return dismountPos;
    }

    /**
     * This is for the ideal y position above the chair we want the dismounting
     * player to teleport to
     * */
    private double getBlockAboveDismountY(Entity mountedEntity, BlockPos localAnchorBlock) {
        AxisAlignedBB collisionBox = mountedEntity.world
                .getBlockState(localAnchorBlock)
                .getCollisionBoundingBox(mountedEntity.world, localAnchorBlock);

        if (collisionBox == null) {
            return localAnchorBlock.getY() + 1D;
        }

        double blockAboveY = localAnchorBlock.getY() + 1D;
        if (collisionBox.maxY <= 1D) return blockAboveY;

        return localAnchorBlock.getY() + collisionBox.maxY + 0.001D;
    }

    private void applyShipAnchoredDismount(
            EntityLivingBase thisEntity, Entity mountedEntity, Vector3d dismountPos
    ) {
        thisEntity.motionX = 0.0D;
        thisEntity.motionY = 0.0D;
        thisEntity.motionZ = 0.0D;
        thisEntity.fallDistance = 0.0F;
        thisEntity.setPositionAndUpdate(dismountPos.x, dismountPos.y, dismountPos.z);
        thisEntity.prevPosX = thisEntity.lastTickPosX = dismountPos.x;
        thisEntity.prevPosY = thisEntity.lastTickPosY = dismountPos.y;
        thisEntity.prevPosZ = thisEntity.lastTickPosZ = dismountPos.z;

        IEntityShipDraggable draggable = thisEntity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable != null) {
            IShipAnchoredMount anchoredMount = mountedEntity.getCapability(
                    VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null
            );
            Optional<PhysicsObject> mountedShip = anchoredMount == null
                    ? Optional.empty()
                    : ValkyrienUtils.getPhysoManagingBlock(mountedEntity.world, anchoredMount.getLocalAnchorBlock());

            draggable.setEntityShipMovementData(new EntityShipMovementData(
                    mountedShip.map(PhysicsObject::getShipData).orElse(null),
                    0,
                    0,
                    new Vector3d(),
                    0
            ));
        }
    }
}
