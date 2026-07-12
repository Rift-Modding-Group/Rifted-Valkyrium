package org.valkyrienskies.mod.common.physics;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.MaterialLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * A helper class that collects full-block collision positions and greedily merges adjacent
 * blocks into larger axis-aligned boxes. Callers add mergeable block positions, then
 * iterate the merged boxes to attach fewer collision shapes while still preserving the
 * original covered block set.
 */
public class GreedyBlockMerger {
    private final Set<BlockPos> positions = new HashSet<>();

    public boolean isMergeableFullBlock(IBlockState state) {
        return state != null
                && !(state.getBlock() instanceof BlockLiquid)
                && !(state.getMaterial() instanceof MaterialLiquid)
                && state.getMaterial().blocksMovement()
                && state.isFullCube();
    }

    public void add(BlockPos pos) {
        this.positions.add(pos.toImmutable());
    }

    public void addAll(Collection<BlockPos> blockPositions) {
        for (BlockPos pos : blockPositions) {
            this.add(pos);
        }
    }

    public void clear() {
        this.positions.clear();
    }

    public boolean isEmpty() {
        return this.positions.isEmpty();
    }

    public void forEachMergedBlockBox(BiConsumer<AxisAlignedBB, List<BlockPos>> consumer) {
        Set<BlockPos> remaining = new HashSet<>(this.positions);

        List<BlockPos> ordered = new ArrayList<>(remaining);
        ordered.sort(Comparator
                .comparingInt(BlockPos::getX)
                .thenComparingInt(BlockPos::getZ)
                .thenComparingInt(BlockPos::getY)
        );

        for (BlockPos start : ordered) {
            if (!remaining.contains(start)) continue;

            int minX = start.getX();
            int minY = start.getY();
            int minZ = start.getZ();
            int maxX = minX;
            int maxY = minY;
            int maxZ = minZ;

            while (this.containsXLayer(remaining, maxX + 1, minY, maxY, minZ, maxZ)) maxX++;
            while (this.containsZLayer(remaining, maxZ + 1, minX, maxX, minY, maxY)) maxZ++;
            while (this.containsYLayer(remaining, maxY + 1, minX, maxX, minZ, maxZ)) maxY++;

            List<BlockPos> mergedBlocks = this.removeBox(remaining, minX, minY, minZ, maxX, maxY, maxZ);
            consumer.accept(
                    new AxisAlignedBB(minX, minY, minZ, maxX + 1D, maxY + 1D, maxZ + 1D),
                    mergedBlocks
            );
        }
    }

    private boolean containsXLayer(
            Set<BlockPos> positions,
            int x,
            int minY,
            int maxY,
            int minZ,
            int maxZ
    ) {
        for (int z = minZ; z <= maxZ; z++) {
            for (int y = minY; y <= maxY; y++) {
                if (!positions.contains(new BlockPos(x, y, z))) return false;
            }
        }
        return true;
    }

    private boolean containsZLayer(
            Set<BlockPos> positions,
            int z,
            int minX,
            int maxX,
            int minY,
            int maxY
    ) {
        for (int x = minX; x <= maxX; x++) {
            for (int y = minY; y <= maxY; y++) {
                if (!positions.contains(new BlockPos(x, y, z))) return false;
            }
        }
        return true;
    }

    private boolean containsYLayer(
            Set<BlockPos> positions,
            int y,
            int minX,
            int maxX,
            int minZ,
            int maxZ
    ) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                if (!positions.contains(new BlockPos(x, y, z))) return false;
            }
        }
        return true;
    }

    private List<BlockPos> removeBox(
            Set<BlockPos> positions,
            int minX,
            int minY,
            int minZ,
            int maxX,
            int maxY,
            int maxZ
    ) {
        List<BlockPos> removed = new ArrayList<>();
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (positions.remove(pos)) removed.add(pos);
                }
            }
        }
        return removed;
    }
}
