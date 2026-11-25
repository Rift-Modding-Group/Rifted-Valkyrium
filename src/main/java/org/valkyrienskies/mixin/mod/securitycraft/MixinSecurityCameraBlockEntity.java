package org.valkyrienskies.mixin.mod.securitycraft;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.geforcemods.securitycraft.entity.camera.SecurityCamera;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.compat.SecurityCraftCompat;

@Pseudo
@Mixin(targets = "net.geforcemods.securitycraft.blockentities.SecurityCameraBlockEntity", remap = false)
public abstract class MixinSecurityCameraBlockEntity {
    @WrapOperation(
            method = "makeEveryoneStopViewingTheCamera",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/geforcemods/securitycraft/entity/camera/SecurityCamera;getPosition()Lnet/minecraft/util/math/BlockPos;",
                    remap = true
            )
    )
    private BlockPos findShipCameraViewer(@NotNull SecurityCamera camera, @NotNull Operation<BlockPos> original) {
        return SecurityCraftCompat.getCameraBlockPos(camera, original.call(camera));
    }
}
