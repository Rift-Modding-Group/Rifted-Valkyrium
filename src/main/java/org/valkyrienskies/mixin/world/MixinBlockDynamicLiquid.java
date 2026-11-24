package org.valkyrienskies.mixin.world;

import net.minecraft.block.BlockDynamicLiquid;
import net.minecraft.block.BlockLiquid;
import net.minecraft.util.math.AxisAlignedBB;
import org.valkyrienskies.mod.common.ships.fluids.ShipWaterVolume;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
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
 * Keeps liquid downflow within the ship's structural height.
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

        return ValkyrienUtils.canFlowDown(world, targetPos.up());
    }

    /**
     * Removes unsupported ship flow before it can spread or settle into a static liquid block.
     */
    @Inject(method = "updateTick", at = @At("HEAD"), cancellable = true)
    private void drainUnsupportedShipLiquid(World world, BlockPos origin, IBlockState state, Random random, CallbackInfo callback) {
        if (world.isRemote || Loader.isModLoaded("fluidlogged_api")) return;
        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(world, origin).orElse(null);
        if (ship == null) return;
        AxisAlignedBB bounds = ShipWaterVolume.getHullBounds(ship);
        if (bounds != null && origin.getY() < bounds.minY) {
            world.setBlockToAir(origin);
            callback.cancel();
            return;
        }
        if (state.getValue(BlockLiquid.LEVEL) == 0) return;

        Material material = state.getMaterial();
        if (ValkyrienUtils.isLiquidFlowSupported(world, origin, state)) return;

        IBlockState current = world.getBlockState(origin);
        if (current.getMaterial() == material && current.getBlock() instanceof BlockLiquid) world.setBlockToAir(origin);
        callback.cancel();
    }
}
