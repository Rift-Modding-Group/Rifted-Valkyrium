package org.valkyrienskies.mixin.mod.securitycraft;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.valkyrienskies.mod.common.compat.SecurityCraftCompat;

@Pseudo
@Mixin(targets = "net.geforcemods.securitycraft.SCClientEventHandler", remap = false)
public abstract class MixinSCClientEventHandler {
    @ModifyVariable(method = "drawCameraOverlay", at = @At("HEAD"), argsOnly = true)
    private static BlockPos findShipCameraOverlay(@NotNull BlockPos pos) {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        return camera == null ? pos : SecurityCraftCompat.getCameraBlockPos(camera, pos);
    }
}
