package org.valkyrienskies.mixin.world;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ships.fluids.ShipWaterVolume;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Random;

@Mixin(BlockLiquid.class)
@ParametersAreNonnullByDefault
public abstract class MixinBlockLiquid {
    @Inject(method = "randomDisplayTick", at = @At("HEAD"), cancellable = true)
    private void suppressWaterEffectsInsideHull(IBlockState state, World world, BlockPos pos, Random random, CallbackInfo callback) {
        if (ShipWaterVolume.isFluid(state) && ShipWaterVolume.isWorldFluidMasked(world, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D)) {
            callback.cancel();
        }
    }
}
