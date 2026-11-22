package org.valkyrienskies.mod.common.ships.block_relocation;

import gnu.trove.set.hash.TLongHashSet;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import valkyrienwarfare.api.ShipBlockBlacklist;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.function.Predicate;

@ParametersAreNonnullByDefault
public class BlockFinder {
    /**
     * Collects eligible blocks in a region, stopping at the caller's detection limit.
     * */
    @NotNull
    public static TLongHashSet findBlocksInRegion(AxisAlignedBB bounds, int maximum, Predicate<BlockPos> allowed) {
        TLongHashSet positions = new TLongHashSet();
        BlockPos minimum = new BlockPos(bounds.minX, Math.max(0D, bounds.minY), bounds.minZ);
        BlockPos maximumPosition = new BlockPos(bounds.maxX - 1D, Math.min(255D, bounds.maxY - 1D), bounds.maxZ - 1D);
        if (minimum.getY() > maximumPosition.getY()) return positions;
        for (BlockPos.MutableBlockPos position : BlockPos.getAllInBoxMutable(minimum, maximumPosition)) {
            if (positions.size() >= maximum) break;
            BlockPos candidate = position.toImmutable();
            if (allowed.test(candidate)) positions.add(candidate.toLong());
        }
        return positions;
    }

    public static SpatialDetector getBlockFinderFor(
            BlockFinderType id, BlockPos start, World worldIn, int maximum, boolean checkCorners,
            ShipBlockBlacklist blockBlacklist
    ) {
        return switch (id) {
            case FIND_ALLOWED_BLOCKS -> new ShipSpawnDetector(
                    start, worldIn, maximum, checkCorners, blockBlacklist
            );
            case FIND_ALL_BLOCKS -> new ShipBlockPosFinder(start, worldIn, maximum, checkCorners);
            case FIND_SINGLE_BLOCK -> new SingleBlockPosDetector(start, worldIn, maximum, checkCorners);
            default -> throw new IllegalArgumentException("Unrecognized detector");
        };
    }

    public enum BlockFinderType {
        FIND_ALLOWED_BLOCKS, FIND_ALL_BLOCKS, FIND_SINGLE_BLOCK
    }

}
