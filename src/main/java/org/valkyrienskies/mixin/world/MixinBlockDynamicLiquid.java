package org.valkyrienskies.mixin.world;

import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

/**
 * puts a limit on water downflow on ships
 */
@Mixin(BlockDynamicLiquid.class)
public abstract class MixinBlockDynamicLiquid {
    @Redirect(
            method = "updateTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/BlockDynamicLiquid;canFlowInto(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z",
                    ordinal = 0
            ),
            require = 1
    )
    private boolean limitShipWaterDownflow(BlockDynamicLiquid liquid, World world, BlockPos targetPos, IBlockState targetState) {
        if (!liquid.canFlowInto(world, targetPos, targetState)) return false;
        if (liquid.getDefaultState().getMaterial() != Material.WATER) return true;

        BlockPos sourcePos = targetPos.up();
        if (ValkyrienUtils.getShipManagingBlock(world, sourcePos).isEmpty()) return true;
        if (world.getBlockState(sourcePos).getMaterial() != Material.WATER) return false;

        BlockPos.MutableBlockPos waterPos = new BlockPos.MutableBlockPos(sourcePos);
        for (int distance = 1; distance <= 4; distance++) { //max downfall of 4 blocks
            waterPos.move(EnumFacing.UP);
            if (world.getBlockState(waterPos).getMaterial() != Material.WATER) return true;
        }
        return false;
    }
}
