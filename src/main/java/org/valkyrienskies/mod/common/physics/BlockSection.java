package org.valkyrienskies.mod.common.physics;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Block sections are basically a group of blocks turned into a collision material for
 * any physics engine this mod's gonna use.
 * */
public record BlockSection (
        @NotNull World world,
        int sectionX,
        int sectionY,
        int sectionZ,
        int contentsHash,
        @NotNull List<BlockData> blocks
) {
    public BlockSection {
        blocks = List.copyOf(blocks);
    }

    public Key key() {
        return new Key(this.world, this.sectionX, this.sectionY, this.sectionZ);
    }

    /**
     * Information about a block that will make up a Block Section
     * */
    public record BlockData(@NotNull BlockPos pos, @NotNull IBlockState state, boolean liquid) {
        public BlockData {
            pos = pos.toImmutable();
        }
    }

    /**
     * Information to distinguish a block section when using in maps
     * */
    public record Key(@NotNull World world, int sectionX, int sectionY, int sectionZ) {
        public static Key fromBlockPos(@NotNull World world, @NotNull BlockPos pos) {
            return new Key(world, pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4);
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Key(World world1, int x, int y, int z))) return false;
            return this.world == world1
                    && this.sectionX == x
                    && this.sectionY == y
                    && this.sectionZ == z;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(this.world);
            result = 31 * result + this.sectionX;
            result = 31 * result + this.sectionY;
            result = 31 * result + this.sectionZ;
            return result;
        }
    }

    /**
     * The range that a block section covers
     * */
    public record Range(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public static Range fromBlockCorners(BlockPos min, BlockPos max) {
            return new Range(
                    min.getX() >> 4,
                    min.getY() >> 4,
                    min.getZ() >> 4,
                    max.getX() >> 4,
                    max.getY() >> 4,
                    max.getZ() >> 4
            );
        }

        public List<Key> keys(@NotNull World world) {
            List<Key> keys = new ArrayList<>();
            for (int sectionX = this.minX; sectionX <= this.maxX; sectionX++) {
                for (int sectionZ = this.minZ; sectionZ <= this.maxZ; sectionZ++) {
                    for (int sectionY = this.minY; sectionY <= this.maxY; sectionY++) {
                        keys.add(new Key(world, sectionX, sectionY, sectionZ));
                    }
                }
            }
            return keys;
        }

        public boolean contains(@NotNull Key key) {
            return key.sectionX >= this.minX
                    && key.sectionX <= this.maxX
                    && key.sectionY >= this.minY
                    && key.sectionY <= this.maxY
                    && key.sectionZ >= this.minZ
                    && key.sectionZ <= this.maxZ;
        }

        public boolean contains(@NotNull Range range) {
            return range.minX >= this.minX
                    && range.maxX <= this.maxX
                    && range.minY >= this.minY
                    && range.maxY <= this.maxY
                    && range.minZ >= this.minZ
                    && range.maxZ <= this.maxZ;
        }
    }

    public static final class Builder {
        @NotNull
        private final World world;
        private final int sectionX;
        private final int sectionY;
        private final int sectionZ;
        @NotNull
        private final List<BlockData> blocks = new ArrayList<>();
        @NotNull
        private final Set<BlockPos> blockPositions = new HashSet<>();
        private int sumHash;
        private int xorHash;

        public Builder(@NotNull World world, int sectionX, int sectionY, int sectionZ) {
            this.world = Objects.requireNonNull(world, "world");
            this.sectionX = sectionX;
            this.sectionY = sectionY;
            this.sectionZ = sectionZ;
        }

        public void addBlock(BlockPos pos, IBlockState state, boolean liquid) {
            BlockData block = new BlockData(pos, state, liquid);
            BlockPos immutablePos = block.pos();
            if (!this.blockPositions.add(immutablePos)) return;

            this.blocks.add(block);
            int blockHash = immutablePos.hashCode();
            blockHash = 31 * blockHash + state.hashCode();
            blockHash = 31 * blockHash + Boolean.hashCode(liquid);
            this.sumHash += blockHash;
            this.xorHash ^= blockHash;
        }

        public boolean isEmpty() {
            return this.blocks.isEmpty();
        }

        public BlockSection build() {
            int contentsHash = this.blocks.size();
            contentsHash = 31 * contentsHash + this.sumHash;
            contentsHash = 31 * contentsHash + this.xorHash;
            return new BlockSection(
                    this.world,
                    this.sectionX,
                    this.sectionY,
                    this.sectionZ,
                    contentsHash,
                    this.blocks
            );
        }
    }
}
