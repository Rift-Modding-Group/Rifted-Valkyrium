package org.valkyrienskies.mixin.mod.securitycraft;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.compat.SecurityCraftCompat;

@Pseudo
@Mixin(targets = "net.geforcemods.securitycraft.renderers.SecurityCameraRenderer", remap = false)
public abstract class MixinSecurityCameraRenderer {
    @NotNull
    @ModifyExpressionValue(
            method = "render(Lnet/geforcemods/securitycraft/blockentities/SecurityCameraBlockEntity;DDDFIF)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/BlockPos;down()Lnet/minecraft/util/math/BlockPos;", remap = true)
    )
    private BlockPos hideViewedShipCamera(@NotNull BlockPos pos) {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        return camera == null ? pos : SecurityCraftCompat.getCameraBlockPos(camera, pos);
    }
}
