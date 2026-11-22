package org.valkyrienskies.mixin.mod.locks;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import melonslise.locks.common.util.Cuboid6i;
import melonslise.locks.common.util.Lockable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.culling.ClippingHelper;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4d;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.TransformedAABB;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

@Pseudo
@Mixin(targets = "melonslise.locks.client.event.LocksClientEvents", remap = false)
public abstract class MixinLocksClientEvents {
    @WrapOperation(
            method = "onRenderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lmelonslise/locks/common/util/Lockable$State;inRange(Lnet/minecraft/util/math/Vec3d;)Z"
            )
    )
    private static boolean checkShipLockRange(Lockable.State state, Vec3d origin, @NotNull Operation<Boolean> original) {
        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(Minecraft.getMinecraft().world, new BlockPos(state.pos)).orElse(null);
        if (ship != null) {
            origin = ship.getShipTransformationManager().getRenderTransform().transform(origin, TransformType.GLOBAL_TO_SUBSPACE);
        }
        return original.call(state, origin);
    }

    @WrapOperation(
            method = "onRenderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lmelonslise/locks/common/util/Lockable$State;inView(Lnet/minecraft/client/renderer/culling/ClippingHelper;Lnet/minecraft/util/math/Vec3d;)Z"
            )
    )
    private static boolean checkShipLockView(Lockable.State state, ClippingHelper clipping, Vec3d origin, @NotNull Operation<Boolean> original) {
        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(Minecraft.getMinecraft().world, new BlockPos(state.pos)).orElse(null);
        if (ship == null) return original.call(state, clipping, origin);

        AxisAlignedBB bounds = new TransformedAABB(state.bb, ship.getShipTransformationManager()
                .getRenderTransform().getSubspaceToGlobal()).getEnclosedAABB();
        return clipping.isBoxInFrustum(
                bounds.minX - origin.x, bounds.minY - origin.y, bounds.minZ - origin.z,
                bounds.maxX - origin.x, bounds.maxY - origin.y, bounds.maxZ - origin.z
        );
    }

    @WrapOperation(
            method = "onRenderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/GlStateManager;translate(DDD)V",
                    ordinal = 0, remap = true
            )
    )
    private static void positionShipLock(
            double x, double y, double z, @NotNull Operation<Void> original, @Local Lockable.State state, @Local Vec3d origin
    ) {
        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(Minecraft.getMinecraft().world, new BlockPos(state.pos)).orElse(null);
        if (ship == null) {
            original.call(x, y, z);
            return;
        }

        ShipTransform transform = ship.getShipTransformationManager().getRenderTransform();
        Vec3d position = transform.transform(state.pos, TransformType.SUBSPACE_TO_GLOBAL);
        original.call(position.x - origin.x, position.y - origin.y, position.z - origin.z);
        AxisAngle4d rotation = new AxisAngle4d(transform.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL));
        GL11.glRotated(Math.toDegrees(rotation.angle), rotation.x, rotation.y, rotation.z);
    }

    @WrapOperation(
            method = "onRenderWorld",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/renderer/RenderGlobal;drawBoundingBox(DDDDDDFFFF)V",
                    remap = true
            )
    )
    private static void renderShipSelection(
            double minX, double minY, double minZ, double maxX, double maxY, double maxZ,
            float red, float green, float blue, float alpha,
            @NotNull Operation<Void> original, @Local Cuboid6i box, @Local Vec3d origin
    ) {
        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(Minecraft.getMinecraft().world, new BlockPos(box.center())).orElse(null);
        if (ship == null) {
            original.call(minX, minY, minZ, maxX, maxY, maxZ, red, green, blue, alpha);
            return;
        }

        ShipTransform transform = ship.getShipTransformationManager().getRenderTransform();
        Vec3d center = box.center();
        Vec3d position = transform.transform(center, TransformType.SUBSPACE_TO_GLOBAL);
        AxisAngle4d rotation = new AxisAngle4d(transform.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL));
        GlStateManager.pushMatrix();
        GlStateManager.translate(position.x - origin.x, position.y - origin.y, position.z - origin.z);
        GL11.glRotated(Math.toDegrees(rotation.angle), rotation.x, rotation.y, rotation.z);
        original.call(
                box.x1 - center.x, box.y1 - center.y, box.z1 - center.z,
                box.x2 - center.x, box.y2 - center.y, box.z2 - center.z,
                red, green, blue, alpha
        );
        GlStateManager.popMatrix();
    }
}
