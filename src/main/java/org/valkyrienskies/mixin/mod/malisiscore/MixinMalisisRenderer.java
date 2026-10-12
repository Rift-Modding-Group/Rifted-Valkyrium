package org.valkyrienskies.mixin.mod.malisiscore;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.client.compat.MalisisCoreClientCompat;

/**
 * Corrects Malisis block-model coordinates while ship chunks are being built.
 * For making the frames of the medieval door and carriage door show up properly on ships.
 * */
@Pseudo
@Mixin(targets = "net.malisis.core.renderer.MalisisRenderer", remap = false)
public abstract class MixinMalisisRenderer {
    @Shadow
    protected Vec3d posOffset;

    @Unique
    private Vec3d valkyrienskies$animatedShipOffset;

    @Inject(
        method = "renderBlock",
        at = @At(
            value = "INVOKE",
            target = "Lnet/malisis/core/renderer/MalisisRenderer;prepare(Lnet/malisis/core/renderer/RenderType;[D)V",
            shift = At.Shift.AFTER
        )
    )
    private void useShipRenderOffset(
            BufferBuilder buffer, IBlockAccess blockAccess, BlockPos blockPos,
            IBlockState blockState, CallbackInfoReturnable<Boolean> callbackInfo
    ) {
        this.posOffset = MalisisCoreClientCompat.getBlockRenderOffset(blockAccess, blockPos, this.posOffset);
    }

    @Inject(
        method = "set(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;)V",
        at = @At("RETURN")
    )
    private void rememberAnimatedShipOffset(IBlockAccess blockAccess, BlockPos blockPos, CallbackInfo callbackInfo) {
        Vec3d worldOffset = new Vec3d(blockPos.getX(), blockPos.getY(), blockPos.getZ());
        Vec3d renderOffset = MalisisCoreClientCompat.getBlockRenderOffset(blockAccess, blockPos, worldOffset);
        this.valkyrienskies$animatedShipOffset = renderOffset == worldOffset ? null : renderOffset;
    }

    @Inject(method = "drawVertex", at = @At("HEAD"))
    private void useAnimatedShipOffset(CallbackInfo callbackInfo) {
        if (this.valkyrienskies$animatedShipOffset != null) {
            this.posOffset = this.valkyrienskies$animatedShipOffset;
        }
    }

    @Inject(method = "reset", at = @At("HEAD"))
    private void forgetAnimatedShipOffset(CallbackInfo callbackInfo) {
        this.valkyrienskies$animatedShipOffset = null;
    }
}
