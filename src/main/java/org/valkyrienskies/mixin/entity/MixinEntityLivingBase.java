package org.valkyrienskies.mixin.entity;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
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

    /**
     * Dismounts riders from third-party chair entities anchored to ships by choosing a safe
     * ship-local exit position, transforming it to world space, and clearing stale ship velocity.
     */
    @Inject(method = "dismountEntity", at = @At("HEAD"), cancellable = true)
    private void dismountFromShipAnchoredSeat(final Entity mountedEntity, final CallbackInfo ci) {
        if (mountedEntity == null) return;

        final IShipAnchoredMount anchoredMount = mountedEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        if (anchoredMount == null || (!anchoredMount.isAnchoredToShip() && !anchoredMount.tryAnchorMount(mountedEntity))) return;

        final Optional<PhysicsObject> mountedShip = ValkyrienUtils.getPhysoManagingBlock(mountedEntity.world, anchoredMount.getLocalAnchorBlock());
        if (mountedShip.isEmpty()) return;

        final EntityLivingBase thisEntity = (EntityLivingBase) (Object) this;
        final BlockPos localAnchorBlock = anchoredMount.getLocalAnchorBlock();
        final EnumFacing front = mountedEntity.getAdjustedHorizontalFacing();
        final EnumFacing side = front.rotateY();
        final int[][] dismountOffsets = {
                {front.getXOffset(), front.getZOffset()},
                {side.getXOffset(), side.getZOffset()},
                {-side.getXOffset(), -side.getZOffset()},
                {-front.getXOffset(), -front.getZOffset()},
                {front.getXOffset() + side.getXOffset(), front.getZOffset() + side.getZOffset()},
                {front.getXOffset() - side.getXOffset(), front.getZOffset() - side.getZOffset()},
                {-front.getXOffset() + side.getXOffset(), -front.getZOffset() + side.getZOffset()},
                {-front.getXOffset() - side.getXOffset(), -front.getZOffset() - side.getZOffset()}
        };
        final double halfWidth = thisEntity.width / 2.0D;
        final double localY = localAnchorBlock.getY() + 1.0D;
        Vector3d localDismountPos = null;

        for (int supportedPass = 0; supportedPass < 2 && localDismountPos == null; supportedPass++) {
            for (final int[] offset : dismountOffsets) {
                final double localX = localAnchorBlock.getX() + 0.5D + offset[0];
                final double localZ = localAnchorBlock.getZ() + 0.5D + offset[1];
                final AxisAlignedBB candidateBox = new AxisAlignedBB(
                        localX - halfWidth,
                        localY,
                        localZ - halfWidth,
                        localX + halfWidth,
                        localY + thisEntity.height,
                        localZ + halfWidth
                );
                final BlockPos supportPos = new BlockPos(localX, localY - 1.0D, localZ);
                final boolean hasSupport =
                        mountedEntity.world.getBlockState(supportPos).isSideSolid(mountedEntity.world, supportPos, EnumFacing.UP) ||
                                mountedEntity.world.getBlockState(supportPos).getMaterial() == Material.WATER;

                if ((supportedPass == 1 || hasSupport) && !mountedEntity.world.collidesWithAnyBlock(candidateBox)) {
                    localDismountPos = new Vector3d(localX, localY, localZ);
                    break;
                }
            }
        }

        if (localDismountPos == null) {
            localDismountPos = new Vector3d(
                    localAnchorBlock.getX() + 0.5D,
                    localY,
                    localAnchorBlock.getZ() + 0.5D
            );
        }

        mountedShip.get().getShipTransform().transformPosition(localDismountPos, TransformType.SUBSPACE_TO_GLOBAL);
        thisEntity.motionX = 0.0D;
        thisEntity.motionY = 0.0D;
        thisEntity.motionZ = 0.0D;
        thisEntity.fallDistance = 0.0F;
        thisEntity.setPositionAndUpdate(localDismountPos.x, localDismountPos.y, localDismountPos.z);
        thisEntity.prevPosX = thisEntity.lastTickPosX = localDismountPos.x;
        thisEntity.prevPosY = thisEntity.lastTickPosY = localDismountPos.y;
        thisEntity.prevPosZ = thisEntity.lastTickPosZ = localDismountPos.z;

        final IEntityShipDraggable draggable = thisEntity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable != null) {
            draggable.setEntityShipMovementData(new EntityShipMovementData(null, 0, 0, new Vector3d(), 0));
        }

        ci.cancel();
    }
}
