package org.valkyrienskies.mixin.world;

import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.Loader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Random;

/**
 * Limits liquid downflow on ships to columns with a nearby supply.
 */
@Mixin(BlockDynamicLiquid.class)
@ParametersAreNonnullByDefault
public abstract class MixinBlockDynamicLiquid {
    @Redirect(
            method = "updateTick",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/block/BlockDynamicLiquid;canFlowInto(Lnet/minecraft/world/World;Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/state/IBlockState;)Z",
                    ordinal = 0
            ),
            require = 0
    )
    private boolean limitShipLiquidDownflow(BlockDynamicLiquid liquid, World world, BlockPos targetPos, IBlockState targetState) {
        if (!liquid.canFlowInto(world, targetPos, targetState)) return false;
        Material material = liquid.getDefaultState().getMaterial();
        if (!material.isLiquid()) return true;

        BlockPos sourcePos = targetPos.up();
        if (ValkyrienUtils.getShipManagingBlock(world, sourcePos).isEmpty()) return true;
        return ValkyrienUtils.canFlowDown(sourcePos, pos -> {
            IBlockState state = world.getBlockState(pos);
            if (state.getMaterial() != material || !(state.getBlock() instanceof BlockLiquid)) return -1;
            return state.getValue(BlockLiquid.LEVEL);
        });
    }

    /**
     * Removes unsupported ship flow before it can spread or settle into a static liquid block.
     */
    @Inject(method = "updateTick", at = @At("HEAD"), cancellable = true)
    private void drainUnsupportedShipLiquid(World world, BlockPos origin, IBlockState state, Random random, CallbackInfo callback) {
        if (world.isRemote || Loader.isModLoaded("fluidlogged_api") || state.getValue(BlockLiquid.LEVEL) == 0) return;
        if (ValkyrienUtils.getShipManagingBlock(world, origin).isEmpty()) return;

        Material material = state.getMaterial();
        boolean supported = ValkyrienUtils.isLiquidFlowSupported(
                origin,
                pos -> {
                    if (world.isOutsideBuildHeight(pos)) return -1;
                    if (!world.isBlockLoaded(pos, false)) return -2;
                    IBlockState neighbor = world.getBlockState(pos);
                    if (neighbor.getMaterial() != material || !(neighbor.getBlock() instanceof BlockLiquid)) return -1;
                    return neighbor.getValue(BlockLiquid.LEVEL);
                },
                (pos, side) -> true
        );
        if (supported) return;

        IBlockState current = world.getBlockState(origin);
        if (current.getMaterial() == material && current.getBlock() instanceof BlockLiquid) world.setBlockToAir(origin);
        callback.cancel();
    }
}
