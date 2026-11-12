package org.valkyrienskies.mixin.world;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.ship_world.IShipWorld;
import org.valkyrienskies.mod.common.entity.EntityMountable;
import org.valkyrienskies.mod.common.ships.chunk_claims.ShipChunkAllocator;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.TransformedAABB;
import valkyrienwarfare.api.TransformType;

import javax.annotation.ParametersAreNonnullByDefault;

@Mixin(World.class)
@ParametersAreNonnullByDefault
public abstract class MixinWorldFluid {
    @Inject(method = "handleMaterialAcceleration", at = @At("RETURN"), cancellable = true)
    private void accelerateInShipFluid(AxisAlignedBB bounds, Material material, Entity entity, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() || !material.isLiquid()) return;
        if (ShipChunkAllocator.isBlockInShipyard(new BlockPos(bounds.getCenter()))) return;
        IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        World world = (World) (Object) this;
        IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld == null || shipWorld.getManager() == null) return;

        for (PhysicsObject ship : shipWorld.getManager().getPhysObjectsInAABB(bounds)) {
            ShipTransform transform = ship.getShipTransform();
            AxisAlignedBB localBounds = new TransformedAABB(bounds, transform, TransformType.GLOBAL_TO_SUBSPACE).getEnclosedAABB();
            double originalX = entity.posX;
            double originalY = entity.posY;
            double originalZ = entity.posZ;
            AxisAlignedBB originalBounds = entity.getEntityBoundingBox();
            double originalMotionX = entity.motionX;
            double originalMotionY = entity.motionY;
            double originalMotionZ = entity.motionZ;
            Vector3d localPosition = new Vector3d(originalX, originalY, originalZ);
            transform.transformPosition(localPosition, TransformType.GLOBAL_TO_SUBSPACE);
            Vector3d localMotion = new Vector3d(originalMotionX, originalMotionY, originalMotionZ);
            transform.transformDirection(localMotion, TransformType.GLOBAL_TO_SUBSPACE);

            entity.posX = localPosition.x;
            entity.posY = localPosition.y;
            entity.posZ = localPosition.z;
            entity.setEntityBoundingBox(new TransformedAABB(originalBounds, transform, TransformType.GLOBAL_TO_SUBSPACE).getEnclosedAABB());
            entity.motionX = localMotion.x;
            entity.motionY = localMotion.y;
            entity.motionZ = localMotion.z;

            boolean inShipFluid = world.handleMaterialAcceleration(localBounds, material, entity);

            Vector3d impulse = new Vector3d(entity.motionX, entity.motionY, entity.motionZ).sub(localMotion);
            transform.transformDirection(impulse, TransformType.SUBSPACE_TO_GLOBAL);
            entity.posX = originalX;
            entity.posY = originalY;
            entity.posZ = originalZ;
            entity.setEntityBoundingBox(originalBounds);
            entity.motionX = originalMotionX + impulse.x;
            entity.motionY = originalMotionY + impulse.y;
            entity.motionZ = originalMotionZ + impulse.z;
            if (inShipFluid) {
                if (draggable != null && !entity.isRiding() && !(entity instanceof EntityMountable)) {
                    draggable.setLastTouchedShip(ship.getShipData());
                    draggable.setTicksSinceTouchedShip(0);
                    draggable.setTicksPartOfGround(0);
                }
                callback.setReturnValue(true);
                break;
            }
        }
    }

    @Inject(method = "isMaterialInBB", at = @At("RETURN"), cancellable = true)
    private void detectShipFluidMaterial(AxisAlignedBB bounds, Material material, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ() || !material.isLiquid()) return;
        if (ShipChunkAllocator.isBlockInShipyard(new BlockPos(bounds.getCenter()))) return;
        World world = (World) (Object) this;
        IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld == null || shipWorld.getManager() == null) return;

        for (PhysicsObject ship : shipWorld.getManager().getPhysObjectsInAABB(bounds)) {
            AxisAlignedBB localBounds = new TransformedAABB(bounds, ship.getShipTransform(), TransformType.GLOBAL_TO_SUBSPACE).getEnclosedAABB();
            if (world.isMaterialInBB(localBounds, material)) {
                callback.setReturnValue(true);
                break;
            }
        }
    }

    @Inject(method = "containsAnyLiquid", at = @At("RETURN"), cancellable = true)
    private void detectShipLiquid(AxisAlignedBB bounds, CallbackInfoReturnable<Boolean> callback) {
        if (callback.getReturnValueZ()) return;
        if (ShipChunkAllocator.isBlockInShipyard(new BlockPos(bounds.getCenter()))) return;
        World world = (World) (Object) this;
        IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld == null || shipWorld.getManager() == null) return;

        for (PhysicsObject ship : shipWorld.getManager().getPhysObjectsInAABB(bounds)) {
            AxisAlignedBB localBounds = new TransformedAABB(bounds, ship.getShipTransform(), TransformType.GLOBAL_TO_SUBSPACE).getEnclosedAABB();
            if (world.containsAnyLiquid(localBounds)) {
                callback.setReturnValue(true);
                break;
            }
        }
    }
}
