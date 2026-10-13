package org.valkyrienskies.mixin.mod.riftlib;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.valkyrienskies.mod.client.render.PhysObjectRenderManager;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.TransformedAABB;
import valkyrienwarfare.api.RiftedValkyriumAPI;
import valkyrienwarfare.api.TransformType;

import javax.annotation.Nullable;

/**
 * For rendering RiftLibrary blocks on ships :D
 */
@Pseudo
@Mixin(targets = "anightdazingzoroark.riftlib.renderers.geo.GeoBlockRendererTicker", remap = false)
public abstract class MixinGeoBlockRendererTicker {
    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/util/math/BlockPos;distanceSqToCenter(DDD)D",
            remap = true
        )
    )
    private double useGlobalShipDistance(
        BlockPos pos, double cameraX, double cameraY, double cameraZ,
        Operation<Double> original, @Local(argsOnly = true) Entity cameraEntity
    ) {
        PhysicsObject physicsObject = getPhysicsObject(cameraEntity, pos);
        if (physicsObject == null) return original.call(pos, cameraX, cameraY, cameraZ);

        Vector3d globalCenter = new Vector3d(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
        physicsObject.getShipTransformationManager().getRenderTransform()
            .transformPosition(globalCenter, TransformType.SUBSPACE_TO_GLOBAL);

        double deltaX = globalCenter.x - cameraX;
        double deltaY = globalCenter.y - cameraY;
        double deltaZ = globalCenter.z - cameraZ;
        return deltaX * deltaX + deltaY * deltaY + deltaZ * deltaZ;
    }

    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/culling/ICamera;isBoundingBoxInFrustum(Lnet/minecraft/util/math/AxisAlignedBB;)Z",
            remap = true
        )
    )
    private boolean useGlobalShipBounds(
        ICamera camera, AxisAlignedBB bounds, Operation<Boolean> original,
        @Local(argsOnly = true) Entity cameraEntity, @Local BlockPos pos
    ) {
        PhysicsObject physicsObject = getPhysicsObject(cameraEntity, pos);
        if (physicsObject == null) return original.call(camera, bounds);

        AxisAlignedBB globalBounds = new TransformedAABB(
            bounds, physicsObject.getShipTransformationManager().getRenderTransform(),
            TransformType.SUBSPACE_TO_GLOBAL
        ).getEnclosedAABB();
        return original.call(camera, globalBounds);
    }

    @WrapOperation(
        method = "render",
        at = @At(
            value = "INVOKE",
            target = "Lanightdazingzoroark/riftlib/renderers/geo/GeoBlockRenderer;render(Lanightdazingzoroark/riftlib/block/AnimatedBlockStateHolder;DDDF)V"
        )
    )
    private void renderInShipSpace(
        @Coerce Object renderer, @Coerce Object holder,
        double x, double y, double z, float partialTicks, Operation<Void> original,
        @Local(argsOnly = true) Entity cameraEntity, @Local BlockPos pos
    ) {
        PhysicsObject physicsObject = getPhysicsObject(cameraEntity, pos);
        if (physicsObject == null) {
            original.call(renderer, holder, x, y, z, partialTicks);
            return;
        }

        PhysObjectRenderManager shipRenderer = physicsObject.getShipRenderer();
        if (shipRenderer == null) return;

        BlockPos offset = shipRenderer.offsetPos;
        GlStateManager.pushMatrix();
        shipRenderer.applyRenderTransform(partialTicks);
        original.call(
            renderer, holder,
            (double) pos.getX() - offset.getX(),
            (double) pos.getY() - offset.getY(),
            (double) pos.getZ() - offset.getZ(),
            partialTicks
        );
        GlStateManager.popMatrix();
    }

    @Nullable
    @Unique
    private static PhysicsObject getPhysicsObject(Entity cameraEntity, BlockPos pos) {
        return RiftedValkyriumAPI.getPhysoManagingBlock(cameraEntity.world, pos)
            .map(PhysicsObject.class::cast)
            .orElse(null);
    }
}
