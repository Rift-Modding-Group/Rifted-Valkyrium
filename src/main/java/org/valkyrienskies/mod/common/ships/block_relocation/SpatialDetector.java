package org.valkyrienskies.mod.common.ships.block_relocation;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;

/**
 * Discovers connected blocks using X-axis spans and a lazily populated chunk cache.
 */
public abstract class SpatialDetector {
    public static final int maxRange = 512;
    public static final int maxRangeHalved = maxRange / 2;
    public static final int maxRangeSquared = maxRange * maxRange;
    public final TIntHashSet foundSet = new TIntHashSet(250);
    public final BlockPos firstBlock;
    private final Chunk[][] cachedChunks;
    private final int minimumChunkX;
    private final int minimumChunkZ;
    public final World worldObj;
    public final int maxSize;
    public final boolean corners;
    private final TIntArrayList detectionQueue = new TIntArrayList(250);
    private final TIntHashSet checkedPositions = new TIntHashSet(250);
    public boolean cleanHouse = false;

    public SpatialDetector(BlockPos start, World worldIn, int maximum, boolean checkCorners) {
        this.firstBlock = start;
        this.worldObj = worldIn;
        this.maxSize = maximum;
        this.corners = checkCorners;
        this.minimumChunkX = (start.getX() - 128) >> 4;
        this.minimumChunkZ = (start.getZ() - 128) >> 4;
        int maximumChunkX = (start.getX() + 128) >> 4;
        int maximumChunkZ = (start.getZ() + 128) >> 4;
        this.cachedChunks = new Chunk[maximumChunkX - this.minimumChunkX + 1][maximumChunkZ - this.minimumChunkZ + 1];
    }

    @NotNull
    protected IBlockState getBlockState(@NotNull BlockPos position) {
        if (position.getY() < 0 || position.getY() >= 256) return Blocks.AIR.getDefaultState();

        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        int cacheX = chunkX - this.minimumChunkX;
        int cacheZ = chunkZ - this.minimumChunkZ;
        if (cacheX < 0 || cacheX >= this.cachedChunks.length || cacheZ < 0 || cacheZ >= this.cachedChunks[cacheX].length) {
            return Blocks.AIR.getDefaultState();
        }

        //only load/generate whats needed
        Chunk chunk = this.cachedChunks[cacheX][cacheZ];
        if (chunk == null) {
            chunk = this.worldObj.getChunk(chunkX, chunkZ);
            this.cachedChunks[cacheX][cacheZ] = chunk;
        }
        return chunk.getBlockState(position);
    }

    public static int getHashWithRespectTo(int realX, int realY, int realZ, BlockPos start) {
        int x = realX - start.getX() + maxRangeHalved;
        int z = realZ - start.getZ() + maxRangeHalved;
        return realY + maxRange * x + maxRangeSquared * z;
    }

    public static BlockPos getPosWithRespectTo(int hash, BlockPos start) {
        int y = hash % maxRange;
        int x = ((hash - y) / maxRange) % maxRange;
        int z = (hash - (x * maxRange) - y) / (maxRangeSquared);
        x -= maxRangeHalved;
        z -= maxRangeHalved;
        return new BlockPos(x + start.getX(), y, z + start.getZ());
    }

    public static void setPosWithRespectTo(int hash, BlockPos start, MutableBlockPos toSet) {
        int y = hash % maxRange;
        int x = ((hash - y) / maxRange) % maxRange;
        int z = (hash - (x * maxRange) - y) / (maxRangeSquared);
        x -= maxRangeHalved;
        z -= maxRangeHalved;
        toSet.setPos(x + start.getX(), y, z + start.getZ());
    }

    public void startDetection() {
        this.calculateSpatialOccupation();
        if (this.cleanHouse) this.foundSet.clear();
    }

    public List<BlockPos> getBlockPosArrayList() {
        List<BlockPos> detectedBlockPos = new ArrayList<BlockPos>(foundSet.size());
        TIntIterator intIter = foundSet.iterator();
        while (intIter.hasNext()) {
            int hash = intIter.next();
            BlockPos fromHash = getPosWithRespectTo(hash, firstBlock);
            if (fromHash.getY() + 128 - firstBlock.getY() < 0) {
                System.err.println("I really hope this doesnt happen");
                return new ArrayList<BlockPos>();
            }
            detectedBlockPos.add(fromHash);
        }
        return detectedBlockPos;
    }

    protected void calculateSpatialOccupation() {
        int firstHash = getHashWithRespectTo(this.firstBlock.getX(), this.firstBlock.getY(), this.firstBlock.getZ(), this.firstBlock);
        this.expandSpan(this.firstBlock.getX(), this.firstBlock.getY(), this.firstBlock.getZ(), firstHash, true);

        MutableBlockPos spanStart = new MutableBlockPos();
        int queueIndex = 0;
        while (queueIndex < this.detectionQueue.size() && !this.cleanHouse && this.foundSet.size() < this.maxSize) {
            int firstSpanHash = this.detectionQueue.get(queueIndex++);
            int lastSpanHash = this.detectionQueue.get(queueIndex++);
            setPosWithRespectTo(firstSpanHash, this.firstBlock, spanStart);
            int spanMinX = spanStart.getX();
            int spanMaxX = spanMinX + (lastSpanHash - firstSpanHash) / maxRange;
            int padding = this.corners ? 1 : 0;

            // Each neighboring row only needs scanning once for the whole span.
            for (int zOffset = -1; zOffset <= 1 && !this.cleanHouse; zOffset++) {
                for (int yOffset = -1; yOffset <= 1 && !this.cleanHouse; yOffset++) {
                    if (yOffset == 0 && zOffset == 0) continue;
                    if (!this.corners && yOffset != 0 && zOffset != 0) continue;
                    int y = spanStart.getY() + yOffset;
                    int z = spanStart.getZ() + zOffset;
                    if (y < 0 || y >= 256) continue;
                    for (int x = spanMinX - padding; x <= spanMaxX + padding
                            && this.foundSet.size() < this.maxSize && !this.cleanHouse; x++) {
                        int hash = firstSpanHash + (x - spanMinX) * maxRange + yOffset + zOffset * maxRangeSquared;
                        x = this.expandSpan(x, y, z, hash, false);
                    }
                }
            }
        }
    }

    /**
     * Adds a complete connected row and queues its endpoints. Returns its rightmost X so
     * callers can skip the newly discovered row instead of queuing every block separately.
     */
    protected int expandSpan(int x, int y, int z, int hash, boolean firstBlock) {
        if (!firstBlock) {
            if (this.foundSet.size() >= this.maxSize || !this.checkedPositions.add(hash)) return x;
            if (!this.isValidExpansion(x, y, z) || this.cleanHouse) return x;
        }
        else this.checkedPositions.add(hash);
        this.foundSet.add(hash);

        int minX = x;
        int minHash = hash;
        while (this.foundSet.size() < this.maxSize && !this.cleanHouse
                && this.checkedPositions.add(minHash - maxRange)) {
            if (!this.isValidExpansion(minX - 1, y, z)) break;
            minX--;
            minHash -= maxRange;
            this.foundSet.add(minHash);
        }

        int maxX = x;
        int maxHash = hash;
        while (this.foundSet.size() < this.maxSize && !this.cleanHouse
                && this.checkedPositions.add(maxHash + maxRange)) {
            if (!this.isValidExpansion(maxX + 1, y, z)) break;
            maxX++;
            maxHash += maxRange;
            this.foundSet.add(maxHash);
        }
        this.detectionQueue.add(minHash);
        this.detectionQueue.add(maxHash);
        return maxX;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public abstract boolean isValidExpansion(int x, int y, int z);
}
