package org.valkyrienskies.mixin.mod.securitycraft;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.valkyrienskies.mod.common.compat.SecurityCraftCompat;

@Pseudo
@Mixin(targets = "net.geforcemods.securitycraft.entity.camera.SecurityCamera", remap = false)
public abstract class MixinSecurityCamera {
    @ModifyArg(
            method = "onUpdate",
            remap = true,
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getBlockState(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/block/state/IBlockState;",
                    remap = true
            )
    )
    private BlockPos checkShipCameraBlock(@NotNull BlockPos pos) {
        return SecurityCraftCompat.getCameraBlockPos((Entity) (Object) this, pos);
    }

    @ModifyArg(
            method = "getBlockEntity",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;getTileEntity(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/tileentity/TileEntity;",
                    remap = true
            )
    )
    private BlockPos findShipCameraBlockEntity(@NotNull BlockPos pos) {
        return SecurityCraftCompat.getCameraBlockPos((Entity) (Object) this, pos);
    }
}
