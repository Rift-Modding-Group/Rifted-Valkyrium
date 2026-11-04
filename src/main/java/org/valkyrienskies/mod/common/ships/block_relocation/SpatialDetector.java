package org.valkyrienskies.mod.common.ships.block_relocation;

import gnu.trove.iterator.TLongIterator;
import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;

/**
 * Discovers connected blocks using X-axis spans and a lazily populated chunk cache.
 */
public abstract class SpatialDetector {
    @NotNull
    public TLongHashSet foundSet = new TLongHashSet(250);
    @NotNull
    public BlockPos firstBlock;
    @NotNull
    private final TLongObjectHashMap<Chunk> cachedChunks = new TLongObjectHashMap<>();
    @NotNull
    public World worldObj;
    public int maxSize;
    public boolean corners;
    @NotNull
    private final TLongArrayList detectionQueue = new TLongArrayList(250);
    @NotNull
    private final TLongHashSet checkedPositions = new TLongHashSet(250);
    @NotNull
    private final MutableBlockPos hashPosition = new MutableBlockPos();
    public boolean cleanHouse = false;

    public SpatialDetector(@NotNull BlockPos start, @NotNull World worldIn, int maximum, boolean checkCorners) {
        this.firstBlock = start;
        this.worldObj = worldIn;
        this.maxSize = maximum;
        this.corners = checkCorners;
    }

    @NotNull
    protected IBlockState getBlockState(@NotNull BlockPos position) {
        if (position.getY() < 0 || position.getY() >= 256) return Blocks.AIR.getDefaultState();

        int chunkX = position.getX() >> 4;
        int chunkZ = position.getZ() >> 4;
        long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
        // Cache only chunks reached by the connected-block search.
        Chunk chunk = this.cachedChunks.get(chunkKey);
        if (chunk == null) {
            chunk = this.worldObj.getChunk(chunkX, chunkZ);
            this.cachedChunks.put(chunkKey, chunk);
        }
        return chunk.getBlockState(position);
    }

    public void startDetection() {
        this.calculateSpatialOccupation();
        if (this.cleanHouse) this.foundSet.clear();
    }

    @NotNull
    public List<BlockPos> getBlockPosArrayList() {
        List<BlockPos> detectedBlockPos = new ArrayList<>(foundSet.size());
        TLongIterator iterator = foundSet.iterator();
        while (iterator.hasNext()) {
            detectedBlockPos.add(BlockPos.fromLong(iterator.next()));
        }
        return detectedBlockPos;
    }

    protected void calculateSpatialOccupation() {
        this.expandSpan(this.firstBlock.getX(), this.firstBlock.getY(), this.firstBlock.getZ(), true);

        MutableBlockPos spanStart = new MutableBlockPos();
        int queueIndex = 0;
        while (queueIndex < this.detectionQueue.size() && !this.cleanHouse && this.foundSet.size() < this.maxSize) {
            long firstSpanHash = this.detectionQueue.get(queueIndex++);
            long lastSpanHash = this.detectionQueue.get(queueIndex++);
            spanStart.setPos(BlockPos.fromLong(firstSpanHash));
            int spanMinX = spanStart.getX();
            int spanMaxX = BlockPos.fromLong(lastSpanHash).getX();
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
                        x = this.expandSpan(x, y, z, false);
                    }
                }
            }
        }
    }

    /**
     * Adds a complete connected row and queues its endpoints. Returns its rightmost X so
     * callers can skip the newly discovered row instead of queuing every block separately.
     */
    protected int expandSpan(int x, int y, int z, boolean firstBlock) {
        long hash = this.hashPosition.setPos(x, y, z).toLong();
        if (!firstBlock) {
            if (this.foundSet.size() >= this.maxSize || !this.checkedPositions.add(hash)) return x;
            if (!this.isValidExpansion(x, y, z) || this.cleanHouse) return x;
        }
        else this.checkedPositions.add(hash);
        this.foundSet.add(hash);

        int minX = x;
        while (this.foundSet.size() < this.maxSize && !this.cleanHouse) {
            long candidateHash = this.hashPosition.setPos(minX - 1, y, z).toLong();
            if (!this.checkedPositions.add(candidateHash)) break;
            if (!this.isValidExpansion(minX - 1, y, z)) break;
            minX--;
            this.foundSet.add(candidateHash);
        }

        int maxX = x;
        while (this.foundSet.size() < this.maxSize && !this.cleanHouse) {
            long candidateHash = this.hashPosition.setPos(maxX + 1, y, z).toLong();
            if (!this.checkedPositions.add(candidateHash)) break;
            if (!this.isValidExpansion(maxX + 1, y, z)) break;
            maxX++;
            this.foundSet.add(candidateHash);
        }
        this.detectionQueue.add(this.hashPosition.setPos(minX, y, z).toLong());
        this.detectionQueue.add(this.hashPosition.setPos(maxX, y, z).toLong());
        return maxX;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public abstract boolean isValidExpansion(int x, int y, int z);
}
