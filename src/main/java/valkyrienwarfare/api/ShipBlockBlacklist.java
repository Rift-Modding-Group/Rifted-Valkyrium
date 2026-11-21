package valkyrienwarfare.api;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.Serializable;

/**
 * Decides whether a block must be excluded while discovering the connected blocks of a ship
 * as well as if a block can be placed on an active ship.
 * The world and position allow an addon to consider tile data, location, dimension, neighboring
 * blocks, or any other assembly-specific rule in addition to the block state.
 */
@FunctionalInterface
@ParametersAreNonnullByDefault
public interface ShipBlockBlacklist extends Serializable {
    /**
     * Tests a candidate block during ship assembly.
     *
     * @param world world containing the candidate
     * @param position immutable position of the candidate
     * @param state current state during assembly, or proposed state during placement
     * @return {@code true} to exclude the block from assembly or reject the state change
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
