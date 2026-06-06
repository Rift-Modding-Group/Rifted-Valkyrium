package org.valkyrienskies.mod.common.ships.block_relocation;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class BlockFinder {
    public static SpatialDetector getBlockFinderFor(BlockFinderType id, BlockPos start, World worldIn, int maximum, boolean checkCorners) {
        return switch (id) {
            case FIND_ALLOWED_BLOCKS -> new ShipSpawnDetector(start, worldIn, maximum, checkCorners);
            case FIND_ALL_BLOCKS -> new ShipBlockPosFinder(start, worldIn, maximum, checkCorners);
            case FIND_SINGLE_BLOCK -> new SingleBlockPosDetector(start, worldIn, maximum, checkCorners);
            default -> throw new IllegalArgumentException("Unrecognized detector");
        };
    }

    public enum BlockFinderType {
        FIND_ALLOWED_BLOCKS, FIND_ALL_BLOCKS, FIND_SINGLE_BLOCK
    }

}
