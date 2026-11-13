package org.valkyrienskies.mod.common.compat;

import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

/**
 * Resolves world height through the loader's build-height checks without loading optional mods.
 * Is for helping out with Deeper Depths mostly.
 */
public class WorldHeightCompat {
    public static int getMinimumY(@NotNull World world) {
        BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos(0, -1, 0);
        if (world.isOutsideBuildHeight(position)) return 0;

        int inside = -1;
        int outside = -2;
        while (!world.isOutsideBuildHeight(position.setPos(0, outside, 0))) {
            inside = outside;
            if (outside == Integer.MIN_VALUE) return outside;
            outside *= 2;
        }
        while ((long) inside - outside > 1L) {
            int middle = (int) (outside + ((long) inside - outside) / 2L);
            if (world.isOutsideBuildHeight(position.setPos(0, middle, 0))) outside = middle;
            else inside = middle;
        }
        return inside;
    }

    public static int getStorageIndex(@NotNull World world, int blockY) {
        int section = blockY >> 4;
        if (section >= 0) return section;

        return Math.max(16, world.getHeight() >> 4) - 1 - section;
    }
}
