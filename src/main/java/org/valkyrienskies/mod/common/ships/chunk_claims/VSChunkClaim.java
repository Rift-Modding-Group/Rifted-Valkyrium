package org.valkyrienskies.mod.common.ships.chunk_claims;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.google.common.collect.Streams;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import org.jspecify.annotations.NonNull;

import javax.annotation.concurrent.Immutable;
import java.beans.ConstructorProperties;
import java.util.HashSet;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * This stores the chunk claims for a PhysicsObject; not the chunks themselves.
 *
 * @author tri0de
 */
@Immutable
public final class VSChunkClaim implements Iterable<ChunkPos> {
    private final ChunkPos centerPos;
    private final Set<Long> claimedChunks;

    public VSChunkClaim(ChunkPos centerPos) {
        this.centerPos = centerPos;
        this.claimedChunks = new HashSet<>();
    }

    @JsonCreator // This annotation tells Jackson to use this constructor for the class
    // The below annotation says which JSON properties correspond to which constructor arguments
    @ConstructorProperties({"centerPos", "claimedChunks"})
    private VSChunkClaim(ChunkPos centerPos, Set<Long> claimedChunks) {
        this.centerPos = centerPos;
        this.claimedChunks = claimedChunks;
    }

    public void writeToNBT(NBTTagCompound toSave) {
        toSave.setLong("centerPos", getChunkPos(centerPos.x, centerPos.z));
        // Using an int array instead of a long array because there is no nbt.setLongArray().
        int[] chunkPositions = new int[this.claimedChunks.size() * 2];
        int i = 0;
        for (Long chunkPos : this.claimedChunks) {
            chunkPositions[i] = getChunkX(chunkPos);
            chunkPositions[i + 1] = getChunkZ(chunkPos);
            i += 2;
        }
        toSave.setIntArray("claimedChunks", chunkPositions);
    }

    /**
     * Checks if a chunk is contained within this {@link VSChunkClaim}
     *
     * @param chunkX The X value of the chunk
     * @param chunkZ The Y value of the chunk
     * @return True if the specified chunk is contained within this {@link VSChunkClaim}
     */
    public boolean containsChunk(int chunkX, int chunkZ) {
        return this.claimedChunks.contains(getChunkPos(chunkX, chunkZ));
    }

    public boolean containsChunk(ChunkPos pos) {
        return containsChunk(pos.x, pos.z);
    }

    /**
     * Checks if a block is contained within this {@link VSChunkClaim}
     *
     * @return True if the specified block is contained within this {@link VSChunkClaim}
     */
    public boolean containsBlock(BlockPos pos) {
        return containsChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    public void addChunkClaim(int chunkX, int chunkZ) {
        long chunkPos = getChunkPos(chunkX, chunkZ);
        this.claimedChunks.add(chunkPos);
    }

    public boolean removeChunkClaim(int chunkX, int chunkZ) {
        long chunkPos = getChunkPos(chunkX, chunkZ);
        return this.claimedChunks.remove(chunkPos);
    }

    @Override
    public String toString() {
        return this.centerPos + ":" + "claim size " + this.claimedChunks.size();
    }

    public BlockPos getRegionCenter() {
        return new BlockPos(this.centerPos.getXStart(), 128, this.centerPos.getZStart());
    }

    /**
     * @return A stream of the {@link ChunkPos} of every chunk inside of this claim.
     */
    public Stream<ChunkPos> stream() {
        return Streams.stream(this);
    }

    /**
     * Convenience function to decompose a {@link ChunkPos} from the iterator into an X and Z
     * @param consumer BiConsumer&lt;x, z&gt;
     */
    public void forEach(BiConsumer<Integer, Integer> consumer) {
        this.forEach(pos -> consumer.accept(pos.x, pos.z));
    }

    @Override
    @NonNull
    public Iterator<ChunkPos> iterator() {
        return new ChunkPosIterator();
    }

    class ChunkPosIterator implements Iterator<ChunkPos> {
        Iterator<Long> chunkLongsIterator = VSChunkClaim.this.claimedChunks.iterator();

        @Override
        public boolean hasNext() {
            return this.chunkLongsIterator.hasNext();
        }

        @Override
        public ChunkPos next() {
            if (!this.hasNext()) throw new NoSuchElementException();

            long next = this.chunkLongsIterator.next();

            int x = getChunkX(next);
            int z = getChunkZ(next);
            return new ChunkPos(x, z);
        }
    }

    // Helper functions, not meant to be exposed outside of VSChunkClaim
    private static int getChunkX(long chunkPos) {
        return (int) (chunkPos & 4294967295L);
    }

    private static int getChunkZ(long chunkPos) {
        return (int) ((chunkPos >> 32) & 4294967295L);
    }

    private static long getChunkPos(int chunkX, int chunkZ) {
        return ChunkPos.asLong(chunkX, chunkZ);
    }

    public ChunkPos getCenterPos() {
        return this.centerPos;
    }

    public Set<Long> getClaimedChunks() {
        return this.claimedChunks;
    }
}
