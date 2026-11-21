package org.valkyrienskies.mixin.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
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
import org.valkyrienskies.mod.common.capability.anchored_mount.ImplCapabilityShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Mixin(EntityLivingBase.class)
public class MixinEntityLivingBase {
    @Shadow
    protected int newPosRotationIncrements;

    /**
     * Gives death experience the same moving frame as the entity before the orb enters the world.
     */
    @WrapOperation(
            method = "onDeathUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;spawnEntity(Lnet/minecraft/entity/Entity;)Z"
            )
    )
    private boolean spawnDeathExperienceOnShip(
            World world,
            Entity spawnedEntity,
            Operation<Boolean> original
    ) {
        if (!world.isRemote && spawnedEntity instanceof EntityXPOrb) {
            EntityDraggable.copyShipTracking((EntityLivingBase) (Object) this, spawnedEntity);
        }
        return original.call(world, spawnedEntity);
    }

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

    /**
     * Keeps aligned riders in place and synchronizes supported exits for modded chairs on the server.
     *
     * TODO: move modded chair dismounting and displacing to their own mixin classes out of here
     * (modded chairs will no longer be dealt with by configs and will be hardcoded by v 2.3+)
     */
    @Inject(method = "dismountEntity", at = @At("HEAD"), cancellable = true)
    private void dismountFromShipAnchoredSeat(@Nullable Entity mountedEntity, @NotNull CallbackInfo ci) {
        EntityLivingBase thisEntity = (EntityLivingBase) (Object) this;
        if (thisEntity.world.isRemote || mountedEntity == null) return;

        IShipAnchoredMount anchoredMount = mountedEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        if (anchoredMount == null || (!anchoredMount.isAnchoredToShip() && !anchoredMount.tryAnchorMount(mountedEntity))) return;

        BlockPos localAnchorBlock = anchoredMount.getLocalAnchorBlock();
        PhysicsObject mountedShip = ValkyrienUtils.getPhysoManagingBlock(mountedEntity.world, localAnchorBlock).orElse(null);
        if (mountedShip == null) return;

        Vector3d dismountPos = new Vector3d(thisEntity.posX, thisEntity.posY, thisEntity.posZ);
        if (!ImplCapabilityShipAnchoredMount.isAlignedEntity(mountedEntity.getClass())) {
            Vec3d localRiderPosition = mountedShip.getShipTransform().transform(thisEntity.getPositionVector(), TransformType.GLOBAL_TO_SUBSPACE);
            List<BlockPos> positions = new ArrayList<>();
            for (int offsetX = -1; offsetX <= 1; offsetX++) {
                for (int offsetZ = -1; offsetZ <= 1; offsetZ++) {
                    if (offsetX == 0 && offsetZ == 0) continue;

                    for (int offsetY = -1; offsetY <= 1; offsetY++) {
                        BlockPos localDismountBlock = localAnchorBlock.add(offsetX, offsetY, offsetZ);
                        Optional<PhysicsObject> supportingShip = ValkyrienUtils.getPhysoManagingBlock(mountedEntity.world, localDismountBlock.down());
                        if (supportingShip.isPresent()
                                && supportingShip.get().getUuid().equals(mountedShip.getUuid())
                                && mountedEntity.world.getBlockState(localDismountBlock.down()).isTopSolid()
                                && !mountedEntity.world.getBlockState(localDismountBlock).getMaterial().isSolid()
                                && !mountedEntity.world.getBlockState(localDismountBlock.up()).getMaterial().isSolid()
                        ) {
                            positions.add(localDismountBlock);
                        }
                    }
                }
            }

            BlockPos localDismountBlock = positions.stream()
                    .min(Comparator.comparingDouble(pos -> {
                        return localRiderPosition.squareDistanceTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D);
                    }))
                    .orElse(localAnchorBlock.up());
            dismountPos.set(
                    localDismountBlock.getX() + 0.5D, localDismountBlock.getY(), localDismountBlock.getZ() + 0.5D);
            mountedShip.getShipTransform().transformPosition(dismountPos, TransformType.SUBSPACE_TO_GLOBAL);
        }

        thisEntity.motionX = 0D;
        thisEntity.motionY = 0D;
        thisEntity.motionZ = 0D;
        thisEntity.fallDistance = 0f;
        EntityDraggable.applyDismountOrientation(thisEntity, mountedShip.getShipTransform());
        thisEntity.setPositionAndUpdate(dismountPos.x, dismountPos.y, dismountPos.z);
        thisEntity.prevPosX = thisEntity.lastTickPosX = dismountPos.x;
        thisEntity.prevPosY = thisEntity.lastTickPosY = dismountPos.y;
        thisEntity.prevPosZ = thisEntity.lastTickPosZ = dismountPos.z;

        IEntityShipDraggable draggable = thisEntity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable != null) {
            draggable.setLastTouchedShip(mountedShip.getShipData());
            draggable.setTicksSinceTouchedShip(0);
            draggable.setTicksPartOfGround(0);
            draggable.setAddedLinearVelocity(new Vector3d());
            draggable.setAddedYawVelocity(0);
        }
        ci.cancel();
    }
}
