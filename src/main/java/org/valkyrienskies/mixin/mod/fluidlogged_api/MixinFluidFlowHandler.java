package org.valkyrienskies.mixin.mod.fluidlogged_api;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import org.valkyrienskies.mod.common.ships.fluids.ShipWaterVolume;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.compat.FluidloggedAPICompat;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Random;

@Pseudo
@Mixin(targets = "git.jbredwards.fluidlogged_api.mod.common.fluid.handler.FluidFlowHandler", remap = false)
@ParametersAreNonnullByDefault
public abstract class MixinFluidFlowHandler {
    /**
     * Keeps downward fluidlogged flow above the lowest non-fluid hull block.
     */
    @ModifyExpressionValue(
        method = "updateDynamic",
        at = @At(
            value = "INVOKE",
            target = "Lgit/jbredwards/fluidlogged_api/mod/common/fluid/util/IFluidUpdateHelper;canFlowInto(IIIILnet/minecraft/util/EnumFacing;ZZ)Z",
            ordinal = 0
        ),
        require = 1,
        remap = false
    )
    private static boolean limitShipLiquidDownflow(
        boolean canFlowInto,
        @Local(argsOnly = true) World world,
        @Local(argsOnly = true) BlockPos sourcePos,
        @Local(argsOnly = true) FluidState originState
    ) {
        if (!canFlowInto) return false;
        if (originState.getDownDensityFace() != EnumFacing.DOWN) return true;
        return ValkyrienUtils.canFlowDown(world, sourcePos);
    }

    /**
     * Removes unsupported ship fluid while preserving the block containing stored fluid.
     */
    @Inject(method = "updateDynamic", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void drainUnsupportedShipLiquid(World world, BlockPos origin, FluidState originState, Random random, CallbackInfo callback) {
        if (world.isRemote || originState.isEmpty() || !originState.isValid()) return;
        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(world, origin).orElse(null);
        if (ship == null) return;
        AxisAlignedBB bounds = ShipWaterVolume.getHullBounds(ship);
        if (bounds != null && origin.getY() < bounds.minY) {
            FluidloggedUtils.setFluidToAir(world, origin, world.getBlockState(origin), 3);
            callback.cancel();
            return;
        }
        if (originState.isSource()) return;

        if (FluidloggedAPICompat.isFlowSupported(world, origin)) return;

        FluidState current = FluidloggedUtils.getFluidState(world, origin);
        if (!current.isEmpty() && FluidloggedUtils.isCompatibleFluid(originState, current)) {
            FluidloggedUtils.setFluidToAir(world, origin, world.getBlockState(origin), 3);
        }
        callback.cancel();
    }
}
