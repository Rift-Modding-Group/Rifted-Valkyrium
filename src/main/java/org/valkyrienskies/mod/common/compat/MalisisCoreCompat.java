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
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.network.MessageMalisisChunkBlockRegistrations;
import valkyrienwarfare.api.event.ShipBlockDataLoadEvent;
import valkyrienwarfare.api.event.ShipBlockRelocationEvent;
import valkyrienwarfare.api.event.ShipChunkSentEvent;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Helper for MalisisCore and by extension MalisisDoors
 */
public class MalisisCoreCompat {
    /**
     * Rebuilds registrations for the initial ship cache or one arriving client chunk.
     * Neighboring blocks can contribute registrations to the arriving chunk.
     */
    @SubscribeEvent
    public static void onShipBlockDataLoad(@Nonnull ShipBlockDataLoadEvent event) {
        World world = event.getWorld();
        Chunk arrivingChunk = event.getArrivingChunk();
        for (BlockPos blockPos : event.getBlockPositions()) {
            IChunkBlock chunkBlock = getChunkBlock(world, blockPos);
            if (chunkBlock == null) continue;

            if (arrivingChunk != null) {
                int blockRange = chunkBlock.blockRange();
                int minimumChunkX = blockPos.getX() - blockRange >> 4;
                int maximumChunkX = blockPos.getX() + blockRange + 1 >> 4;
                int minimumChunkZ = blockPos.getZ() - blockRange >> 4;
                int maximumChunkZ = blockPos.getZ() + blockRange + 1 >> 4;
                if (arrivingChunk.x >= minimumChunkX && arrivingChunk.x <= maximumChunkX
                        && arrivingChunk.z >= minimumChunkZ && arrivingChunk.z <= maximumChunkZ) {
                    addRegistration(arrivingChunk, blockPos);
                }
            }
            else {
                List<Chunk> affectedChunks = ChunkBlockHandler.getAffectedChunks(world, blockPos.getX(), blockPos.getZ(), chunkBlock.blockRange());
                for (Chunk affectedChunk : affectedChunks) addRegistration(affectedChunk, blockPos);
            }
        }
    }

    /**
     * Keeps MalisisCore's tracker up to date when we change a block without using the usual
     * {@link Chunk#setBlockState(BlockPos, IBlockState)} path.
     */
    @SubscribeEvent
    public static void onBlockRelocationPost(@Nonnull ShipBlockRelocationEvent.PostCopied event) {
        Chunk destinationChunk = event.getDestinationChunk();
        // Detached assembly chunks are registered after the complete ship is installed.
        if (!destinationChunk.isLoaded()) return;

        World world = destinationChunk.getWorld();
        BlockPos blockPos = event.getDestinationPos();
        IChunkBlock oldChunkBlock = getChunkBlock(event.getOldState());
        if (oldChunkBlock != null) {
            List<Chunk> affectedChunks = ChunkBlockHandler.getAffectedChunks(world, blockPos.getX(), blockPos.getZ(), oldChunkBlock.blockRange());
            for (Chunk affectedChunk : affectedChunks) {
                removeRegistration(affectedChunk, blockPos);
            }
        }

        IChunkBlock newChunkBlock = getChunkBlock(event.getNewState());
        if (newChunkBlock != null) {
            List<Chunk> affectedChunks = ChunkBlockHandler.getAffectedChunks(world, blockPos.getX(), blockPos.getZ(), newChunkBlock.blockRange());
            for (Chunk affectedChunk : affectedChunks) {
                addRegistration(affectedChunk, blockPos);
            }
        }
    }

    @SubscribeEvent
    public static void onShipChunkSent(@Nonnull ShipChunkSentEvent event) {
        sendChunkBlockRegistrations(event.getChunk(), event.getPlayer());
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
     * Moves block registrations between source and destination chunks and updates nearby players.
     */
    @SubscribeEvent
    public static void onBlockRelocation(@Nonnull ShipBlockRelocationEvent.PreCopied event) {
        if (!(event.getWorld() instanceof WorldServer world)) return;

        List<BlockPos> relocatedPositions = new ArrayList<>();
        for (BlockPos source : event.getSourceBlocks()) {
            IChunkBlock chunkBlock = getChunkBlock(world, source);
            if (chunkBlock == null) continue;

            for (Chunk affectedChunk : ChunkBlockHandler.getAffectedChunks(world, source.getX(), source.getZ(), chunkBlock.blockRange())) {
                removeRegistration(affectedChunk, source);
            }
            relocatedPositions.add(source.add(event.getOffset()));
        }
        if (relocatedPositions.isEmpty()) return;

        event.afterRelocation(() -> {
            Set<Chunk> affectedChunks = new HashSet<>();
            for (BlockPos destination : relocatedPositions) {
                IChunkBlock chunkBlock = getChunkBlock(world, destination);
                if (chunkBlock == null) continue;

                for (Chunk affectedChunk : ChunkBlockHandler.getAffectedChunks(world, destination.getX(), destination.getZ(), chunkBlock.blockRange())) {
                    addRegistration(affectedChunk, destination);
                    affectedChunks.add(affectedChunk);
                }
            }

            for (Chunk affectedChunk : affectedChunks) {
                for (EntityPlayer player : world.playerEntities) {
                    if (!(player instanceof EntityPlayerMP serverPlayer)) continue;
                    if (world.getPlayerChunkMap().isPlayerWatchingChunk(serverPlayer, affectedChunk.x, affectedChunk.z)) {
                        sendChunkBlockRegistrations(affectedChunk, serverPlayer);
                    }
                }
            }
        });
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

    private static void addRegistration(@Nonnull Chunk chunk, @Nonnull BlockPos blockPos) {
        ChunkBlockHandler.get().chunks(chunk).add(chunk, blockPos.toImmutable());
        if (!chunk.getWorld().isRemote) chunk.markDirty();
    }

    private static void removeRegistration(@Nonnull Chunk chunk, @Nonnull BlockPos blockPos) {
        ChunkBlockHandler.get().chunks(chunk).remove(chunk, blockPos.toImmutable());
        if (!chunk.getWorld().isRemote) chunk.markDirty();
    }
}
