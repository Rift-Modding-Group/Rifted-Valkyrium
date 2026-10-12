package org.valkyrienskies.mod.common.compat;

import net.malisis.core.block.IComponent;
import net.malisis.core.util.chunkblock.ChunkBlockHandler;
import net.malisis.core.util.chunkblock.IChunkBlock;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.network.MessageMalisisChunkBlockRegistrations;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.datastructures.IBlockPosSetAABB;

import javax.annotation.Nonnull;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper for MalisisCore and by extension MalisisDoors
 */
public class MalisisCoreCompat {
    /**
     * Finds the Malisis blocks on a loaded ship and adds them back to the chunk tracker.
     */
    public static void rebuildChunkBlockRegistrations(@Nonnull PhysicsObject physicsObject) {
        IBlockPosSetAABB blockPositions = physicsObject.getBlockPositions();
        if (blockPositions == null) return;

        World world = physicsObject.getWorld();
        for (BlockPos blockPos : blockPositions) {
            IChunkBlock chunkBlock = getChunkBlock(world, blockPos);
            if (chunkBlock == null) continue;

            List<Chunk> affectedChunks = ChunkBlockHandler.getAffectedChunks(world, blockPos.getX(), blockPos.getZ(), chunkBlock.blockRange());
            for (Chunk affectedChunk : affectedChunks) {
                addRegistration(affectedChunk, blockPos);
            }
        }
    }

    /**
     * Adds Malisis blocks to a ship chunk that just reached the client.
     * A large door can reach into nearby chunks, so this checks every block on the ship.
     */
    public static void rebuildChunkBlockRegistrations(@Nonnull PhysicsObject physicsObject, @Nonnull Chunk arrivingChunk) {
        IBlockPosSetAABB blockPositions = physicsObject.getBlockPositions();
        if (blockPositions == null) return;

        World world = physicsObject.getWorld();
        for (BlockPos blockPos : blockPositions) {
            IChunkBlock chunkBlock = getChunkBlock(world, blockPos);
            if (chunkBlock == null || !affectsChunk(blockPos, chunkBlock.blockRange(), arrivingChunk)) {
                continue;
            }
            addRegistration(arrivingChunk, blockPos);
        }
    }

    /**
     * Keeps MalisisCore's tracker up to date when we change a block without using the usual
     * {@link Chunk#setBlockState(BlockPos, IBlockState)} path.
     */
    public static void updateChunkBlockRegistration(
            @Nonnull World world, @Nonnull BlockPos blockPos,
            @Nonnull IBlockState oldState, @Nonnull IBlockState newState
    ) {
        IChunkBlock oldChunkBlock = getChunkBlock(oldState);
        if (oldChunkBlock != null) {
            List<Chunk> affectedChunks = ChunkBlockHandler.getAffectedChunks(world, blockPos.getX(), blockPos.getZ(), oldChunkBlock.blockRange());
            for (Chunk affectedChunk : affectedChunks) {
                removeRegistration(affectedChunk, blockPos);
            }
        }

        IChunkBlock newChunkBlock = getChunkBlock(newState);
        if (newChunkBlock != null) {
            List<Chunk> affectedChunks = ChunkBlockHandler.getAffectedChunks(world, blockPos.getX(), blockPos.getZ(), newChunkBlock.blockRange());
            for (Chunk affectedChunk : affectedChunks) {
                addRegistration(affectedChunk, blockPos);
            }
        }
    }

    /**
     * Sends a chunk's tracked Malisis blocks to one player.
     */
    public static void sendChunkBlockRegistrations(@Nonnull Chunk chunk, @Nonnull EntityPlayerMP player) {
        Set<BlockPos> registrations = ChunkBlockHandler.get().chunks(chunk).get(chunk);
        if (!registrations.isEmpty()) {
            ValkyrienSkiesMod.physWrapperNetwork.sendTo(new MessageMalisisChunkBlockRegistrations(chunk, registrations), player);
        }
    }

    /**
     * Updates nearby players after a ship's blocks have been placed back into the world.
     */
    public static void sendRelocatedChunkBlockRegistrations(@Nonnull World world, @Nonnull Iterable<BlockPos> relocatedPositions) {
        if (!(world instanceof WorldServer serverWorld)) return;

        Set<Chunk> affectedChunks = new HashSet<>();
        for (BlockPos blockPos : relocatedPositions) {
            IChunkBlock chunkBlock = getChunkBlock(world, blockPos);
            if (chunkBlock == null) continue;

            affectedChunks.addAll(ChunkBlockHandler.getAffectedChunks(world, blockPos.getX(), blockPos.getZ(), chunkBlock.blockRange()));
        }

        for (Chunk affectedChunk : affectedChunks) {
            for (EntityPlayer player : serverWorld.playerEntities) {
                if (!(player instanceof EntityPlayerMP serverPlayer)) continue;
                if (serverWorld.getPlayerChunkMap().isPlayerWatchingChunk(serverPlayer, affectedChunk.x, affectedChunk.z)) {
                    sendChunkBlockRegistrations(affectedChunk, serverPlayer);
                }
            }
        }
    }

    /**
     * Adds received positions one at a time because MalisisCore's addAll skips existing entries.
     */
    public static void mergeChunkBlockRegistrations(@Nonnull Chunk chunk, @Nonnull Set<BlockPos> registrations) {
        for (BlockPos blockPos : registrations) addRegistration(chunk, blockPos);
    }

    //-----internal helpers-----
    private static IChunkBlock getChunkBlock(@Nonnull World world, @Nonnull BlockPos blockPos) {
        IBlockState blockState = world.getBlockState(blockPos);
        return getChunkBlock(blockState);
    }

    private static IChunkBlock getChunkBlock(@Nonnull IBlockState blockState) {
        return IComponent.getComponent(IChunkBlock.class, blockState.getBlock());
    }

    private static boolean affectsChunk(@Nonnull BlockPos blockPos, int blockRange, @Nonnull Chunk chunk) {
        int minimumChunkX = blockPos.getX() - blockRange >> 4;
        int maximumChunkX = blockPos.getX() + blockRange + 1 >> 4;
        int minimumChunkZ = blockPos.getZ() - blockRange >> 4;
        int maximumChunkZ = blockPos.getZ() + blockRange + 1 >> 4;
        return chunk.x >= minimumChunkX && chunk.x <= maximumChunkX && chunk.z >= minimumChunkZ && chunk.z <= maximumChunkZ;
    }

    private static void addRegistration(@Nonnull Chunk chunk, @Nonnull BlockPos blockPos) {
        ChunkBlockHandler.get().chunks(chunk).add(chunk, blockPos.toImmutable());
        if (!chunk.getWorld().isRemote) chunk.markDirty();
    }

    private static void removeRegistration(@Nonnull Chunk chunk, @Nonnull BlockPos blockPos) {
        ChunkBlockHandler.get().chunks(chunk).remove(chunk, blockPos.toImmutable());
        if (!chunk.getWorld().isRemote) chunk.markDirty();
    }
}
