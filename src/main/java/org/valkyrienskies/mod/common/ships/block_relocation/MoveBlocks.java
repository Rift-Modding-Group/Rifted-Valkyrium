package org.valkyrienskies.mod.common.ships.block_relocation;

import gnu.trove.map.hash.TLongObjectHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fml.common.Loader;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.compat.FluidloggedAPICompat;
import org.valkyrienskies.mod.common.compat.MalisisCoreCompat;
import org.valkyrienskies.mod.common.physics.BlockPhysicsDetails;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.datastructures.IBlockPosSetAABB;

import javax.annotation.Nullable;

public class MoveBlocks {
    /**
     * Copies a ship to the world using its loaded source chunks and a destination chunk cache.
     * Returns the changed chunks for one lighting pass after every block and tile is placed.
     */
    @NotNull
    public static TLongObjectHashMap<Chunk> copyBlocksToWorld(
            @NotNull World world, @NotNull IBlockPosSetAABB blockPositions,
            @NotNull Iterable<Chunk> sourceChunks, @NotNull BlockPos centerDifference
    ) {
        TLongObjectHashMap<Chunk> changedChunks = new TLongObjectHashMap<>();
        TLongObjectHashMap<Chunk> sourceChunksByPosition = new TLongObjectHashMap<>();
        for (Chunk chunk : sourceChunks) sourceChunksByPosition.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);
        BlockPos.MutableBlockPos destination = new BlockPos.MutableBlockPos();
        for (BlockPos source : blockPositions) {
            destination.setPos(source.getX() - centerDifference.getX(), source.getY() - centerDifference.getY(), source.getZ() - centerDifference.getZ());
            int y = destination.getY();
            if (y < 0 || y >= 256) continue;

            int chunkX = destination.getX() >> 4;
            int chunkZ = destination.getZ() >> 4;
            long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
            Chunk targetChunk = changedChunks.get(chunkKey);
            if (targetChunk == null) {
                targetChunk = world.getChunk(chunkX, chunkZ);
                changedChunks.put(chunkKey, targetChunk);
            }
            Chunk sourceChunk = sourceChunksByPosition.get(ChunkPos.asLong(source.getX() >> 4, source.getZ() >> 4));
            if (sourceChunk == null) throw new IllegalStateException("Missing ship chunk for " + source);
            IBlockState previousState = targetChunk.getBlockState(destination);
            IBlockState movedState = sourceChunk.getBlockState(source);
            if (previousState.getBlock().hasTileEntity(previousState) || targetChunk.getTileEntityMap().containsKey(destination)) {
                world.removeTileEntity(destination);
            }
            ExtendedBlockStorage[] storage = targetChunk.getBlockStorageArray();
            int section = y >> 4;
            if (storage[section] == null) storage[section] = new ExtendedBlockStorage(section << 4, world.provider.hasSkyLight());
            storage[section].set(destination.getX() & 15, y & 15, destination.getZ() & 15, movedState);

            //update state for fluidlogged blocks by fluidlogged api
            if (Loader.isModLoaded("fluidlogged_api")) FluidloggedAPICompat.copyFluidState(sourceChunk, source, targetChunk, destination);

            //update state for multiblock doors by malisis doors
            if (Loader.isModLoaded("malisiscore")) MalisisCoreCompat.updateChunkBlockRegistration(world, destination, previousState, movedState);

            //normal update
            world.notifyBlockUpdate(destination, previousState, movedState, 2);

            if (movedState.getBlock().hasTileEntity(movedState) || sourceChunk.getTileEntityMap().containsKey(source)) {
                copyTileEntityToPos(world, source, destination.toImmutable(), null);
            }
        }
        return changedChunks;
    }

    /**
     * @param physicsObject Used when we're using this to copy from world to physics object; should be null when other
     *                      way around.
     */
    public static void copyBlockToPos(World world, BlockPos oldPos, BlockPos newPos, @Nullable PhysicsObject physicsObject) {
        // To avoid any updates crap, just edit the chunk data array directly.
        // These look switched, but trust me they aren't
        IBlockState oldState = world.getBlockState(newPos);
        IBlockState newState = world.getBlockState(oldPos);
        // A hacky way to set the block state within the chunk while avoiding any block updates.
        Chunk chunkToSet = world.getChunk(newPos);
        int storageIndex = newPos.getY() >> 4;
        // Check that we're placing the block in a valid position
        if (storageIndex < 0 || storageIndex >= chunkToSet.getBlockStorageArray().length) {
            // Invalid position, abort!
            return;
        }
        if (chunkToSet.getBlockStorageArray()[storageIndex] == Chunk.NULL_BLOCK_STORAGE) {
            chunkToSet.getBlockStorageArray()[storageIndex] = new ExtendedBlockStorage(storageIndex << 4,
                true);
        }
        chunkToSet.getBlockStorageArray()[storageIndex].set(newPos.getX() & 15, newPos.getY() & 15, newPos.getZ() & 15, newState);
        if (Loader.isModLoaded("fluidlogged_api")) {
            FluidloggedAPICompat.copyFluidState(world.getChunk(oldPos), oldPos, chunkToSet, newPos);
        }
        if (Loader.isModLoaded("malisiscore")) {
            MalisisCoreCompat.updateChunkBlockRegistration(world, newPos, oldState, newState);
        }
        // Only want to send the update to clients and nothing else, so we use flag 2.
        world.notifyBlockUpdate(newPos, oldState, newState, 2);

        // If this block is force block, then add it to the activeForcePositions list of the ship.
        if (physicsObject != null && BlockPhysicsDetails.isBlockProvidingForce(newState)) {
            physicsObject.getShipData().activeForcePositions.add(newPos);
        }

        // Now that we've copied the block to the position, copy the tile entity
        copyTileEntityToPos(world, oldPos, newPos, physicsObject);
    }

    public static void copyTileEntityToPos(World world, BlockPos oldPos, BlockPos newPos, PhysicsObject physicsObject) {
        // Make a copy of the tile entity at oldPos to newPos
        TileEntity worldTile = world.getTileEntity(oldPos);
        if (worldTile != null) {
            NBTTagCompound tileEntNBT = new NBTTagCompound();
            TileEntity newInstance;
            if (worldTile instanceof IRelocationAwareTile) {
                ShipData shipData = null;
                if (physicsObject != null) {
                    shipData = physicsObject.getShipData();
                }
                newInstance = ((IRelocationAwareTile) worldTile).createRelocatedTile(newPos, shipData);
            } else {
                tileEntNBT = worldTile.writeToNBT(tileEntNBT);
                // Change the block position to be inside of the Ship
                tileEntNBT.setInteger("x", newPos.getX());
                tileEntNBT.setInteger("y", newPos.getY());
                tileEntNBT.setInteger("z", newPos.getZ());
                newInstance = TileEntity.create(world, tileEntNBT);
            }

            try {
                world.setTileEntity(newPos, newInstance);
                if (physicsObject != null) {
                    physicsObject.onSetTileEntity(newPos, newInstance);
                }
                newInstance.markDirty();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

}
