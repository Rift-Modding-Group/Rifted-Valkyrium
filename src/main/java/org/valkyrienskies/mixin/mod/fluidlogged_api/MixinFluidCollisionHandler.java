package org.valkyrienskies.mixin.mod.fluidlogged_api;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.IBlockAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.compat.FluidloggedAPICompat;
import org.valkyrienskies.mod.common.ships.fluids.ShipWaterVolume;

import javax.annotation.ParametersAreNonnullByDefault;

@Pseudo
@Mixin(targets = "git.jbredwards.fluidlogged_api.mod.common.fluid.handler.FluidCollisionHandler", remap = false)
@ParametersAreNonnullByDefault
public abstract class MixinFluidCollisionHandler {
    @Inject(method = "isEntityInsideMaterial", at = @At("HEAD"), cancellable = true, remap = false)
    private static void maskEntityWater(
            IBlockAccess access, BlockPos pos, IBlockState state, Entity entity,
            double height, Material material, boolean testingHead, CallbackInfoReturnable<Boolean> callback
    ) {
        if (material != Material.WATER) return;
        boolean masked;
        if (testingHead) {
            masked = ShipWaterVolume.isWorldWaterMasked(entity.world, entity.posX, entity.posY + entity.getEyeHeight(), entity.posZ);
        }
        else masked = ShipWaterVolume.isWorldWaterMasked(entity.world, entity.getEntityBoundingBox(), pos);
        if (masked) callback.setReturnValue(false);
    }

    @Inject(
            method = "isAABBInsideMaterial(Lnet/minecraft/block/state/IBlockState;Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/AxisAlignedBB;Lnet/minecraft/block/material/Material;)Z",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private static void maskMaterialWater(
            IBlockState state, IBlockAccess access, BlockPos pos, AxisAlignedBB bounds,
            Material material, CallbackInfoReturnable<Boolean> callback
    ) {
        if (material == Material.WATER && FluidloggedAPICompat.isWorldWaterMasked(access, pos, bounds)) callback.setReturnValue(false);
    }

    @Inject(
            method = "isAABBInsideLiquid(Lnet/minecraft/world/IBlockAccess;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/util/math/AxisAlignedBB;Lnet/minecraft/block/state/IBlockState;Ljava/lang/Object;Z)Ljava/lang/Boolean;",
            at = @At("HEAD"), cancellable = true, remap = false
    )
    private static void maskLiquidWater(
            IBlockAccess access, BlockPos pos, AxisAlignedBB bounds, IBlockState state,
            Object heightCache, boolean fancy, CallbackInfoReturnable<Boolean> callback
    ) {
        if (state.getMaterial() == Material.WATER && FluidloggedAPICompat.isWorldWaterMasked(access, pos, bounds)) callback.setReturnValue(false);
    }
}
