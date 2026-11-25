package org.valkyrienskies.mixin.mod.securitycraft;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.valkyrienskies.mod.common.compat.SecurityCraftCompat;

@Pseudo
@Mixin(targets = "net.geforcemods.securitycraft.network.server.SetDefaultCameraViewingDirection$Handler", remap = false)
public abstract class MixinSetDefaultCameraViewingDirection {
    @ModifyArg(
            method = "lambda$onMessage$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getTileEntity(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/tileentity/TileEntity;",
                    remap = true
            )
    )
    private static BlockPos saveShipCameraDirection(@NotNull BlockPos pos, @Local Entity camera) {
        return SecurityCraftCompat.getCameraBlockPos(camera, pos);
    }
}
