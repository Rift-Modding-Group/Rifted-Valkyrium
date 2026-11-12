package org.valkyrienskies.mixin.mod.fluidlogged_api;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import net.minecraft.block.material.Material;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Random;

@Pseudo
@Mixin(targets = "git.jbredwards.fluidlogged_api.mod.common.fluid.handler.FluidFlowHandler", remap = false)
@ParametersAreNonnullByDefault
public abstract class MixinFluidFlowHandler {
    /**
     * Limits downward liquid flow on ships while allowing fluidlogged supply in the column.
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
        Material material = originState.getMaterial();
        if (!material.isLiquid()) return true;
        if (ValkyrienUtils.getShipManagingBlock(world, sourcePos).isEmpty()) return true;
        return ValkyrienUtils.canFlowDown(sourcePos, pos -> {
            FluidState state = FluidloggedUtils.getFluidState(world, pos);
            if (state.isEmpty() || state.getMaterial() != material) return -1;
            return state.getLevel();
        });
    }

    /**
     * Removes unsupported ship fluid while preserving the block containing stored fluid.
     */
    @Inject(method = "updateDynamic", at = @At("HEAD"), cancellable = true, require = 1, remap = false)
    private static void drainUnsupportedShipLiquid(World world, BlockPos origin, FluidState originState, Random random, CallbackInfo callback) {
        if (world.isRemote || originState.isEmpty() || !originState.isValid() || originState.isSource()) return;
        if (ValkyrienUtils.getShipManagingBlock(world, origin).isEmpty()) return;

        boolean supported = ValkyrienUtils.isLiquidFlowSupported(
                origin,
                pos -> {
                    if (world.isOutsideBuildHeight(pos)) return -1;
                    if (!world.isBlockLoaded(pos, false)) return -2;
                    FluidState state = FluidloggedUtils.getFluidState(world, pos);
                    if (state.isEmpty() || !state.isValid() || !FluidloggedUtils.isCompatibleFluid(originState, state)) return -1;
                    if (state.isSource()) return 0;
                    return Math.max(1, state.getLevel());
                },
                (pos, side) -> {
                    if (!FluidloggedUtils.canFluidFlow(world, pos, world.getBlockState(pos), side)) return false;
                    BlockPos neighbor = pos.offset(side);
                    if (!world.isBlockLoaded(neighbor, false)) return true;
                    return FluidloggedUtils.canFluidFlow(world, neighbor, world.getBlockState(neighbor), side.getOpposite());
                }
        );
        if (supported) return;

        FluidState current = FluidloggedUtils.getFluidState(world, origin);
        if (!current.isEmpty() && FluidloggedUtils.isCompatibleFluid(originState, current)) {
            FluidloggedUtils.setFluidToAir(world, origin, world.getBlockState(origin), 3);
        }
        callback.cancel();
    }
}
