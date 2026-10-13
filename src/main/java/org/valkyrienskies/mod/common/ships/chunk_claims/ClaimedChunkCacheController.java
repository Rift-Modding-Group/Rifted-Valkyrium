package org.valkyrienskies.mod.common.ships.chunk_claims;

import net.minecraft.server.management.PlayerChunkMap;
import net.minecraft.server.management.PlayerChunkMapEntry;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import javax.annotation.Nonnull;
import java.util.*;

/**
 * The ClaimedChunkCacheController is a chunk cache controller used by the {@link PhysicsObject}. It
 * keeps all of a ship's chunks in cache for fast access.
 */
public class ClaimedChunkCacheController implements Iterable<Chunk> {

    /**
     * You should ideally not be accessing this directly
     */
    private final PhysicsObject parent;
    private final World world;

    private final Map<Long, Chunk> claimedChunks;

    /**
     * This constructor is expensive; it loads all the chunks when it's called. Be warned.
     *
     * @param parent The PhysicsObject that is using this ChunkCacheController
     */
    public ClaimedChunkCacheController(PhysicsObject parent) {
        this.world = parent.getWorld();
        this.parent = parent;
        this.claimedChunks = new HashMap<>();
        loadChunksIntoCache();
    }

    /**
     * Retrieves a chunk from cache from its absolute position.
     *
     * @param chunkX The X position of the chunk
     * @param chunkZ The Z position of the chunk
     * @return The chunk from the cache
     */
    public Chunk getChunkAt(int chunkX, int chunkZ) {
        this.throwIfOutOfBounds(chunkX, chunkZ);

        return this.claimedChunks.get(ChunkPos.asLong(chunkX, chunkZ));
    }

    /**
     * Retrieves a chunk from cache from its absolute position.
     *
     * @param chunkX The X position of the chunk
     * @param chunkZ The Z position of the chunk
     * @param chunk  The chunk to cache.
     */
    private void setChunkAt(int chunkX, int chunkZ, Chunk chunk) {
        this.throwIfOutOfBounds(chunkX, chunkZ);

        this.claimedChunks.put(ChunkPos.asLong(chunkX, chunkZ), chunk);
    }

    /**
     * Throws a ChunkNotInClaimException if (chunkX, chunkZ) isn't a part of the given VSChunkClaim.
     */
    private void throwIfOutOfBounds(int chunkX, int chunkZ) {
        if (!this.parent.getChunkClaim().containsChunk(chunkX, chunkZ)) {
            throw new ChunkNotInClaimException(chunkX, chunkZ);
        }
    }

    /**
     * Loads chunks from the world, and puts them into the cache.
     */
    private void loadChunksIntoCache() {
        if (VSConfig.showAnnoyingDebugOutput) {
            System.out.println("Loading chunks for " + this.parent.getShipData());
        }
        VSChunkClaim claim = this.parent.getShipData().getChunkClaim();

        claim.forEach((x, z) -> {
            // Added try catch to prevent ships deleting themselves because of a failed tile entity load.
            try {
                Chunk chunk = this.world.getChunk(x, z);
                if (chunk.isEmpty()) { // if (chunk instanceof EmptyChunk) { [Changed because EmptyChunk is a 'client' class]
                    if (VSConfig.showAnnoyingDebugOutput) {
                        System.out.println("Why did we put an empty chunk at (" + x + "," + z + ")?");
                    }
                }

                // Do this to get it re-integrated into the world
                if (!this.world.isRemote) {
                    // Inject the entry into the player chunk map.
                    PlayerChunkMap map = ((WorldServer) this.world).getPlayerChunkMap();
                    PlayerChunkMapEntry entry = map.getOrCreateEntry(x, z);
                    // Very important! We must update the chunk field of the entry to prevent old chunk objects from living on.
                    // If this entry already existed and we forget, then we will corrupt the entry by having different chunks
                    // in the world vs in the entries!
                    entry.chunk = chunk;
                    entry.sentToPlayers = true;
                    entry.players = this.parent.getWatchingPlayers();
                }

                chunk.getTileEntityMap().forEach(this.parent::onSetTileEntity);

                this.setChunkAt(x, z, chunk);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    public void deleteShipChunksFromWorld() {
        PlayerChunkMap map = ((WorldServer) world).getPlayerChunkMap();

        for (Chunk chunk : this) {
            // First delete all the TileEntities in the chunk
            List<BlockPos> chunkTilesPos = new ArrayList<>(chunk.getTileEntityMap().keySet());
            for (BlockPos tilePos : chunkTilesPos) {
                chunk.getWorld().removeTileEntity(tilePos);
            }
            // Then replace all the chunk's block storage with null.
            for (int i = 0; i < 16; i++) {
                chunk.getBlockStorageArray()[i] = null;
            }
            chunk.markDirty();

            // Finally, remove the PlayerChunkMapEntry that was watching that chunk, if it still exists.
            // The chunk can remain loaded after its player map entry has already been removed.
            PlayerChunkMapEntry entry = map.getEntry(chunk.x, chunk.z);
            /*
            if (entry == null) {
                // This should be impossible, throw exception if it happens.
                throw new IllegalStateException("How did the entry at " + chunk.x + " : " + chunk.z + " return as null?");
            }
             */
            if (entry != null) map.removeEntry(entry);
        }
    }

    @Nonnull
    @Override
    public Iterator<Chunk> iterator() {
        return new ArrayList<>(this.claimedChunks.values()).iterator();
    }

    /**
     * Replace an old chunk object with a new one in this cache.
     */
    @SideOnly(Side.CLIENT)
    public void updateChunk(@Nonnull Chunk chunk) {
        this.setChunkAt(chunk.x, chunk.z, chunk);
    }
}