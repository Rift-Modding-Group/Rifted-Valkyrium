package org.valkyrienskies.mixin.mod.effortlessbuilding;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockRendererDispatcher;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.joml.AxisAngle4d;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

@Pseudo
@Mixin(targets = "nl.requios.effortlessbuilding.render.RenderHandler", remap = false)
public abstract class MixinRenderHandler {
    @Inject(
        method = "renderBlockPreview(Lnet/minecraft/client/renderer/BlockRendererDispatcher;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
        at = @At("HEAD"),
        remap = false
    )
    private static void beginShipBlockPreview(BlockRendererDispatcher dispatcher, BlockPos position, IBlockState state, CallbackInfo callbackInfo) {
        GL11.glPushMatrix();
    }

    /**
     * turn a ship block preview directly to global and applies the ship's rendered rotation
     */
    @Redirect(
        method = "renderBlockPreview(Lnet/minecraft/client/renderer/BlockRendererDispatcher;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/renderer/GlStateManager;translate(FFF)V",
            ordinal = 0,
            remap = true
        ),
        remap = false
    )
    private static void translateShipBlockPreview(
            float x, float y, float z,
            BlockRendererDispatcher dispatcher, BlockPos position, IBlockState state
    ) {
        if (!applyShipTransform(position)) GlStateManager.translate(x, y, z);
    }

    @Inject(
        method = "renderBlockPreview(Lnet/minecraft/client/renderer/BlockRendererDispatcher;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)V",
        at = @At("RETURN"),
        remap = false
    )
    private static void endShipBlockPreview(BlockRendererDispatcher dispatcher, BlockPos position, IBlockState state, CallbackInfo callbackInfo) {
        GL11.glPopMatrix();
    }

    @Inject(
        method = "renderBlockOutline(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Vec3d;)V",
        at = @At("HEAD"),
        remap = false
    )
    private static void beginShipBlockOutline(BlockPos firstPosition, BlockPos secondPosition, Vec3d color, CallbackInfo callbackInfo) {
        GL11.glPushMatrix();
        applyShipTransform(firstPosition);
    }

    @ModifyVariable(
        method = "renderBlockOutline(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Vec3d;)V",
        at = @At("STORE"),
        index = 3,
        remap = false
    )
    private static AxisAlignedBB rebaseShipBlockRangeOutline(AxisAlignedBB bounds, BlockPos firstPosition) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || ValkyrienUtils.getPhysoManagingBlock(minecraft.world, firstPosition).isEmpty()) {
            return bounds;
        }
        return bounds.offset(-firstPosition.getX(), -firstPosition.getY(), -firstPosition.getZ());
    }

    @Inject(
        method = "renderBlockOutline(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/Vec3d;)V",
        at = @At("RETURN"),
        remap = false
    )
    private static void endShipBlockOutline(BlockPos firstPosition, BlockPos secondPosition, Vec3d color, CallbackInfo callbackInfo) {
        GL11.glPopMatrix();
    }

    @Inject(
        method = "renderBlockOutline(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/AxisAlignedBB;Lnet/minecraft/util/math/Vec3d;)V",
        at = @At("HEAD"),
        remap = false
    )
    private static void beginShipBlockOutline(BlockPos position, AxisAlignedBB bounds, Vec3d color, CallbackInfo callbackInfo) {
        GL11.glPushMatrix();
        applyShipTransform(position);
    }

    @ModifyVariable(
        method = "renderBlockOutline(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/AxisAlignedBB;Lnet/minecraft/util/math/Vec3d;)V",
        at = @At("STORE"),
        index = 3,
        remap = false
    )
    private static AxisAlignedBB rebaseShipBoundedOutline(AxisAlignedBB bounds, BlockPos position) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null || ValkyrienUtils.getPhysoManagingBlock(minecraft.world, position).isEmpty()) {
            return bounds;
        }
        return bounds.offset(-position.getX(), -position.getY(), -position.getZ());
    }

    @Inject(
        method = "renderBlockOutline(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/AxisAlignedBB;Lnet/minecraft/util/math/Vec3d;)V",
        at = @At("RETURN"),
        remap = false
    )
    private static void endShipBlockOutline(BlockPos position, AxisAlignedBB bounds, Vec3d color, CallbackInfo callbackInfo) {
        GL11.glPopMatrix();
    }

    private static boolean applyShipTransform(BlockPos position) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.world == null) return false;

        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(minecraft.world, position).orElse(null);
        if (ship == null) return false;

        ShipTransform transform = ship.getShipTransformationManager().getRenderTransform();
        Vector3d globalPosition = new Vector3d(position.getX(), position.getY(), position.getZ());
        transform.transformPosition(globalPosition, TransformType.SUBSPACE_TO_GLOBAL);
        AxisAngle4d rotation = new AxisAngle4d(transform.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL));
        GL11.glTranslated(globalPosition.x, globalPosition.y, globalPosition.z);
        GL11.glRotated(Math.toDegrees(rotation.angle), rotation.x, rotation.y, rotation.z);
        return true;
    }
}
