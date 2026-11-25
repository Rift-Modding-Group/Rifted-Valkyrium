package org.valkyrienskies.mixin.mod.securitycraft;

import com.llamalad7.mixinextras.sugar.Local;
import net.geforcemods.securitycraft.entity.camera.SecurityCamera;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.compat.SecurityCraftCompat;

@Pseudo
@Mixin(targets = "net.geforcemods.securitycraft.entity.camera.CameraController", remap = false)
public abstract class MixinCameraController {
    @ModifyArg(
            method = "moveViewHorizontally",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;",
                    remap = true
            )
    )
    private static BlockPos rotateShipCamera(@NotNull BlockPos pos, @Local(argsOnly = true) SecurityCamera camera) {
        return SecurityCraftCompat.getCameraBlockPos(camera, pos);
    }

    @ModifyVariable(method = "toggleRedstone", at = @At("STORE"), ordinal = 0)
    private static BlockPos powerShipCamera(@NotNull BlockPos pos, @Local(argsOnly = true) SecurityCamera camera) {
        return SecurityCraftCompat.getCameraBlockPos(camera, pos);
    }
}
