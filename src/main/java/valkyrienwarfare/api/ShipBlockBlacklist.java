package valkyrienwarfare.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Decides whether a block must be excluded while discovering the blocks of a ship during assembly.
 * The world and position allow an addon to consider tile data, location, dimension, neighboring
 * blocks, or any other assembly-specific rule in addition to the block state.
 */
@FunctionalInterface
@ParametersAreNonnullByDefault
public interface ShipBlockBlacklist {
    /**
     * Tests a candidate block during ship assembly.
     *
     * @param world world containing the candidate
     * @param position immutable position of the candidate
     * @param state current state during assembly
     * @return {@code true} to exclude the block from assembly
     */
    boolean isBlacklisted(World world, BlockPos position, IBlockState state);

    /**
     * Creates the minimal default blacklist used by API overloads without an explicit policy.
     *
     * @return a blacklist that excludes blocks treated as air by Minecraft or Forge
     */
    static ShipBlockBlacklist airOnly() {
        return (world, position, state) -> state.getBlock().isAir(state, world, position);
    }
}
