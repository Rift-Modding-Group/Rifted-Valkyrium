package org.valkyrienskies.mod.common.ships.block_relocation;

import gnu.trove.map.hash.TLongObjectHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.block.material.Material;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.EnumFacing;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.Loader;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.compat.WorldHeightCompat;
import org.valkyrienskies.mod.common.compat.FluidloggedAPICompat;
import org.valkyrienskies.mod.common.physics.BlockPhysicsDetails;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.fluids.ShipWaterVolume;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.mod.common.util.datastructures.IBlockPosSetAABB;
import valkyrienwarfare.api.event.ShipBlockRelocationEvent;

import javax.annotation.Nullable;
import java.util.BitSet;
import java.util.HashSet;
import java.util.Set;

public class MoveBlocks {
    /**
     * Copies a ship to the world using its loaded source chunks and a destination chunk cache.
     * Returns the changed chunks for one lighting pass after every block and tile is placed.
     */
    @NotNull
    public static TLongObjectHashMap<Chunk> copyBlocksToWorld(
            @NotNull World world, @NotNull IBlockPosSetAABB blockPositions,
            @NotNull Iterable<Chunk> sourceChunks, @NotNull BlockPos centerDifference,
            @NotNull ShipWaterVolume.Snapshot waterVolume
    ) {
        TLongObjectHashMap<Chunk> changedChunks = new TLongObjectHashMap<>();
        TLongObjectHashMap<Chunk> sourceChunksByPosition = new TLongObjectHashMap<>();
        for (Chunk chunk : sourceChunks) sourceChunksByPosition.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);
        BlockPos.MutableBlockPos destination = new BlockPos.MutableBlockPos();
        for (BlockPos source : blockPositions) {
            destination.setPos(source.getX() - centerDifference.getX(), source.getY() - centerDifference.getY(), source.getZ() - centerDifference.getZ());
            int y = destination.getY();
            if (world.isOutsideBuildHeight(destination)) continue;

            Chunk sourceChunk = sourceChunksByPosition.get(ChunkPos.asLong(source.getX() >> 4, source.getZ() >> 4));
            if (sourceChunk == null) throw new IllegalStateException("Missing ship chunk for " + source);
            IBlockState movedState = sourceChunk.getBlockState(source);
            // Check supply before writing destination terrain, while every ship source is still in place.
            if (!ValkyrienUtils.isLiquidFlowSupported(world, source, movedState)) continue;

            int chunkX = destination.getX() >> 4;
            int chunkZ = destination.getZ() >> 4;
            long chunkKey = ChunkPos.asLong(chunkX, chunkZ);
            Chunk targetChunk = changedChunks.get(chunkKey);
            if (targetChunk == null) {
                targetChunk = world.getChunk(chunkX, chunkZ);
                changedChunks.put(chunkKey, targetChunk);
            }
            IBlockState previousState = targetChunk.getBlockState(destination);
            if ((movedState.getMaterial().isLiquid() || movedState.getBlock() instanceof IFluidBlock)
                    && !previousState.getBlock().isAir(previousState, world, destination)) continue;

            if (previousState.getBlock().hasTileEntity(previousState) || targetChunk.getTileEntityMap().containsKey(destination)) {
                world.removeTileEntity(destination);
            }
            ExtendedBlockStorage[] storage = targetChunk.getBlockStorageArray();
            int section = WorldHeightCompat.getStorageIndex(world, y);
            if (storage[section] == null) storage[section] = new ExtendedBlockStorage(y & ~15, world.provider.hasSkyLight());
            storage[section].set(destination.getX() & 15, y & 15, destination.getZ() & 15, movedState);

            MinecraftForge.EVENT_BUS.post(new ShipBlockRelocationEvent.PostCopied(
                    sourceChunk, source, targetChunk, destination, previousState, movedState, true, false
            ));

            //normal update
            world.notifyBlockUpdate(destination, previousState, movedState, 2);

            if (movedState.getBlock().hasTileEntity(movedState) || sourceChunk.getTileEntityMap().containsKey(source)) {
                copyTileEntityToPos(world, source, destination.toImmutable(), null);
            }
        }

        // Install the complete hull before restoring its displaced air on the destination grid.
        boolean fluidlogged = Loader.isModLoaded("fluidlogged_api");
        BitSet restoredAir = new BitSet();
        for (int index = waterVolume.air().nextSetBit(0); index >= 0; index = waterVolume.air().nextSetBit(index + 1)) {
            destination.setPos(
                    waterVolume.origin().getX() + index % waterVolume.sizeX() - centerDifference.getX(),
                    waterVolume.origin().getY() + index / waterVolume.sizeX() % waterVolume.sizeY() - centerDifference.getY(),
                    waterVolume.origin().getZ() + index / (waterVolume.sizeX() * waterVolume.sizeY()) - centerDifference.getZ()
            );
            if (world.isOutsideBuildHeight(destination)) continue;
            long chunkKey = ChunkPos.asLong(destination.getX() >> 4, destination.getZ() >> 4);
            Chunk targetChunk = changedChunks.get(chunkKey);
            if (targetChunk == null) targetChunk = world.getChunk(destination);
            IBlockState previousState = targetChunk.getBlockState(destination);
            IBlockState newState = previousState;
            boolean changed = false;
            if (previousState.getMaterial() == Material.WATER) {
                if (previousState.getBlock().hasTileEntity(previousState) || targetChunk.getTileEntityMap().containsKey(destination)) {
                    world.removeTileEntity(destination);
                }
                ExtendedBlockStorage storage = targetChunk.getBlockStorageArray()[WorldHeightCompat.getStorageIndex(world, destination.getY())];
                newState = Blocks.AIR.getDefaultState();
                storage.set(destination.getX() & 15, destination.getY() & 15, destination.getZ() & 15, newState);
                changed = true;
            }
            if (fluidlogged) changed |= FluidloggedAPICompat.removeStoredWater(targetChunk, destination);
            if (changed) {
                restoredAir.set(index);
                targetChunk.markDirty();
                changedChunks.put(chunkKey, targetChunk);
                world.notifyBlockUpdate(destination, previousState, newState, 2);
            }
        }

        // Resume bordering water after every dry cell is installed, so real openings can still flood.
        Set<BlockPos> updatedWater = new HashSet<>();
        EnumFacing[] directions = EnumFacing.values();
        for (int index = restoredAir.nextSetBit(0); index >= 0; index = restoredAir.nextSetBit(index + 1)) {
            int x = index % waterVolume.sizeX();
            int y = index / waterVolume.sizeX() % waterVolume.sizeY();
            int z = index / (waterVolume.sizeX() * waterVolume.sizeY());
            destination.setPos(
                    waterVolume.origin().getX() + x - centerDifference.getX(),
                    waterVolume.origin().getY() + y - centerDifference.getY(),
                    waterVolume.origin().getZ() + z - centerDifference.getZ()
            );
            for (EnumFacing side : directions) {
                int nx = x + side.getXOffset();
                int ny = y + side.getYOffset();
                int nz = z + side.getZOffset();
                if (nx >= 0 && nx < waterVolume.sizeX() && ny >= 0 && ny < waterVolume.sizeY()
                        && nz >= 0 && nz < waterVolume.sizeZ()
                        && restoredAir.get(nx + waterVolume.sizeX() * (ny + waterVolume.sizeY() * nz))) continue;
                BlockPos neighbor = destination.offset(side);
                if (world.isOutsideBuildHeight(neighbor) || !world.isBlockLoaded(neighbor, false)) continue;
                IBlockState fluid = world.getBlockState(neighbor);
                if (fluidlogged) fluid = FluidloggedAPICompat.getFluidOrReal(world, neighbor);
                if (fluid.getMaterial() == Material.WATER && updatedWater.add(neighbor)) {
                    world.neighborChanged(neighbor, Blocks.AIR, destination);
                }
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
        if (world.isOutsideBuildHeight(newPos)) return;
        int storageIndex = WorldHeightCompat.getStorageIndex(world, newPos.getY());
        // Check that we're placing the block in a valid position
        if (storageIndex < 0 || storageIndex >= chunkToSet.getBlockStorageArray().length) {
            // Invalid position, abort!
            return;
        }
        if (chunkToSet.getBlockStorageArray()[storageIndex] == Chunk.NULL_BLOCK_STORAGE) {
            chunkToSet.getBlockStorageArray()[storageIndex] = new ExtendedBlockStorage(newPos.getY() & ~15,
                true);
        }
        chunkToSet.getBlockStorageArray()[storageIndex].set(newPos.getX() & 15, newPos.getY() & 15, newPos.getZ() & 15, newState);
        MinecraftForge.EVENT_BUS.post(new ShipBlockRelocationEvent.PostCopied(
                world.getChunk(oldPos), oldPos, chunkToSet, newPos, oldState, newState, false, false
        ));
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
