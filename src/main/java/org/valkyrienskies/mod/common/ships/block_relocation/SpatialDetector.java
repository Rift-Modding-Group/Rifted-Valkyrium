package org.valkyrienskies.mod.common.ships.block_relocation;

import gnu.trove.iterator.TIntIterator;
import gnu.trove.list.array.TIntArrayList;
import gnu.trove.set.hash.TIntHashSet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;

/**
 * Used to efficiently detect a connected set of blocks TODO: Incorporate a scanline technique to
 * improve detection speed
 *
 * @author thebest108
 */
public abstract class SpatialDetector {

    public static final int maxRange = 512;
    public static final int maxRangeHalved = maxRange / 2;
    public static final int maxRangeSquared = maxRange * maxRange;
    public final TIntHashSet foundSet = new TIntHashSet(250);
    public final BlockPos firstBlock;
    public final MutableBlockPos tempPos = new MutableBlockPos();
    public final ChunkCache cache;
    public final World worldObj;
    public final int maxSize;
    public final boolean corners;
    private final TIntArrayList detectionQueue = new TIntArrayList(250);
    private final TIntHashSet checkedPositions = new TIntHashSet(250);
    // public int totalCalls = 0;
    public boolean cleanHouse = false;

    public SpatialDetector(BlockPos start, World worldIn, int maximum, boolean checkCorners) {
        this.firstBlock = start;
        this.worldObj = worldIn;
        this.maxSize = maximum;
        this.corners = checkCorners;
        BlockPos minPos = new BlockPos(start.getX() - 128, 0, start.getZ() - 128);
        BlockPos maxPos = new BlockPos(start.getX() + 128, 255, start.getZ() + 128);
        this.cache = new ChunkCache(worldIn, minPos, maxPos, 0);
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

    public final void startDetection() {
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
        int firstHash = this.firstBlock.getY() + maxRange * maxRangeHalved + maxRangeSquared * maxRangeHalved;
        this.foundSet.add(firstHash);
        this.checkedPositions.add(firstHash);
        this.detectionQueue.add(firstHash);

        MutableBlockPos inRealWorld = new MutableBlockPos();
        int queueIndex = 0;
        while (queueIndex < this.detectionQueue.size() && !this.cleanHouse) {
            int hash = this.detectionQueue.get(queueIndex++);
            setPosWithRespectTo(hash, this.firstBlock, inRealWorld);
            if (this.corners) {
                for (int xOffset = -1; xOffset <= 1; xOffset++) {
                    for (int yOffset = -1; yOffset <= 1; yOffset++) {
                        for (int zOffset = -1; zOffset <= 1; zOffset++) {
                            if (xOffset == 0 && yOffset == 0 && zOffset == 0) continue;
                            int neighborHash = hash + xOffset * maxRange + yOffset + zOffset * maxRangeSquared;
                            this.tryExpanding(
                                inRealWorld.getX() + xOffset,
                                inRealWorld.getY() + yOffset,
                                inRealWorld.getZ() + zOffset,
                                neighborHash
                            );
                        }
                    }
                }
            }
            else {
                this.tryExpanding(inRealWorld.getX() + 1, inRealWorld.getY(), inRealWorld.getZ(), hash + maxRange);
                this.tryExpanding(inRealWorld.getX() - 1, inRealWorld.getY(), inRealWorld.getZ(), hash - maxRange);
                this.tryExpanding(inRealWorld.getX(), inRealWorld.getY() + 1, inRealWorld.getZ(), hash + 1);
                this.tryExpanding(inRealWorld.getX(), inRealWorld.getY() - 1, inRealWorld.getZ(), hash - 1);
                this.tryExpanding(inRealWorld.getX(), inRealWorld.getY(), inRealWorld.getZ() + 1, hash + maxRangeSquared);
                this.tryExpanding(inRealWorld.getX(), inRealWorld.getY(), inRealWorld.getZ() - 1, hash - maxRangeSquared);
            }
        }
    }

    protected void tryExpanding(int x, int y, int z, int hash) {
        if (foundSet.size() >= maxSize || !checkedPositions.add(hash)) return;
        if (!isValidExpansion(x, y, z)) return;

        foundSet.add(hash);
        detectionQueue.add(hash);
    }

    public abstract boolean isValidExpansion(int x, int y, int z);
}
