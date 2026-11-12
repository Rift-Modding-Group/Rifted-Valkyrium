package org.valkyrienskies.mixin.mod.fluidlogged_api;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import git.jbredwards.fluidlogged_api.api.util.FluidState;
import git.jbredwards.fluidlogged_api.api.util.FluidloggedUtils;
import net.minecraft.block.material.Material;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

@Pseudo
@Mixin(targets = "git.jbredwards.fluidlogged_api.mod.common.fluid.handler.FluidFlowHandler", remap = false)
public abstract class MixinFluidFlowHandler {
    /**
     * Limits downward water flow on ships while allowing fluidlogged water in the column.
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
    private static boolean limitShipWaterDownflow(
        boolean canFlowInto,
        @Local(argsOnly = true) World world,
        @Local(argsOnly = true) BlockPos sourcePos,
        @Local(argsOnly = true) FluidState originState
    ) {
        if (!canFlowInto) return false;
        if (originState.getMaterial() != Material.WATER) return true;
        if (ValkyrienUtils.getShipManagingBlock(world, sourcePos).isEmpty()) return true;
        if (FluidloggedUtils.getFluidState(world, sourcePos).getMaterial() != Material.WATER) return false;

        BlockPos.MutableBlockPos waterPos = new BlockPos.MutableBlockPos(sourcePos);
        for (int distance = 1; distance <= 4; distance++) {
            waterPos.move(EnumFacing.UP);
            if (FluidloggedUtils.getFluidState(world, waterPos).getMaterial() != Material.WATER) return true;
        }
        return false;
    }
}
