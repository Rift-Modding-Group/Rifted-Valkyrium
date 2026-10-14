package org.valkyrienskies.mod.common.ships.ship_world;

import com.google.common.collect.ImmutableList;
import gnu.trove.iterator.TLongIterator;
import gnu.trove.map.hash.TLongObjectHashMap;
import gnu.trove.set.hash.TLongHashSet;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraftforge.fml.common.Loader;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.jspecify.annotations.NonNull;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.compat.FairyLightsCompat;
import org.valkyrienskies.mod.common.compat.MalisisCoreCompat;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.BlockPhysicsDetails;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.block_relocation.BlockFinder;
import org.valkyrienskies.mod.common.ships.block_relocation.IRelocationAwareTile;
import org.valkyrienskies.mod.common.ships.block_relocation.SpatialDetector;
import org.valkyrienskies.mod.common.ships.physics_data.BasicCenterOfMassProvider;
import org.valkyrienskies.mod.common.ships.physics_data.IPhysicsObjectCenterOfMassProvider;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.util.multithreaded.CalledFromWrongThreadException;
import org.valkyrienskies.mod.common.util.multithreaded.VSWorldPhysicsLoop;
import valkyrienwarfare.api.TransformType;

import javax.annotation.Nonnull;
import java.util.*;

public class WorldServerShipManager implements IPhysObjectWorld {
    private final WorldServer world;
    private final VSWorldPhysicsLoop physicsLoop;
    private final Thread physicsThread;
    private final WorldShipLoadingController loadingController;
    private final Map<UUID, PhysicsObject> loadedShips;
    // Use LinkedHashSet as a queue because it preserves order and doesn't allow duplicates
    private final LinkedHashSet<ImmutableTriple<BlockPos, ShipData, BlockFinder.BlockFinderType>> spawnQueue;
    private final LinkedHashSet<UUID> loadQueue, unloadQueue, backgroundLoadQueue;
    private final Set<UUID> loadingInBackground;
    private volatile ImmutableList<PhysicsObject> threadSafeLoadedShips;

    public WorldServerShipManager(World world) {
        this.world = (WorldServer) world;
        this.physicsLoop = new VSWorldPhysicsLoop(world);
        this.loadingController = new WorldShipLoadingController(this);
        this.loadedShips = new HashMap<>();
        this.spawnQueue = new LinkedHashSet<>();
        this.loadQueue = new LinkedHashSet<>();
        this.unloadQueue = new LinkedHashSet<>();
        this.backgroundLoadQueue = new LinkedHashSet<>();
        this.loadingInBackground = new HashSet<>();
        this.threadSafeLoadedShips = ImmutableList.of();

        this.physicsThread = new Thread(this.physicsLoop);
        this.physicsThread.start();
    }

    private void enforceGameThread() {
        if (!this.world.isCallingFromMinecraftThread()) {
            //throw new CalledFromWrongThreadException("Wrong thread calling code: " + Thread.currentThread());
            ValkyrienSkiesMod.LOGGER.warn("Wrong thread calling code: " + Thread.currentThread());
        }
    }

    @Override
    public void onWorldUnload() {
        for (PhysicsObject physicsObject : this.loadedShips.values()) {
            physicsObject.getPhysicsCollideWith().close();
        }
        this.threadSafeLoadedShips = ImmutableList.of();
        this.physicsLoop.kill();
        this.physicsThread.interrupt();
        try {
            this.physicsThread.join(5000L);
        }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        this.loadedShips.clear();
    }

    @Override
    public PhysicsObject getPhysObjectFromUUID(@Nonnull UUID shipID) throws CalledFromWrongThreadException {
        if (this.world.isCallingFromMinecraftThread()) {
            return this.loadedShips.get(shipID);
        }
        for (PhysicsObject physicsObject : this.threadSafeLoadedShips) {
            if (shipID.equals(physicsObject.getShipData().getUuid())) {
                return physicsObject;
            }
        }
        return null;
    }

    @Nonnull
    @Override
    public List<PhysicsObject> getPhysObjectsInAABB(@Nonnull AxisAlignedBB toCheck) throws CalledFromWrongThreadException {
        List<PhysicsObject> nearby = new ArrayList<>();
        for (PhysicsObject ship : getAllLoadedPhysObj()) {
            if (toCheck.intersects(ship.getShipBB())) {
                nearby.add(ship);
            }
        }
        return nearby;
    }

    public void tick() {
        // First destroy any ships that want to be destroyed (copy blocks from ship to world, and then unload)
        Iterator<Map.Entry<UUID, PhysicsObject>> iterator = this.loadedShips.entrySet().iterator();
        while (iterator.hasNext()) {
            PhysicsObject physicsObject = iterator.next().getValue();
            if (physicsObject.shouldShipBeDestroyed()) {
                // Copy ship blocks to the world
                physicsObject.destroyShip();
                // Then remove the ship from the world, and the ship map.
                QueryableShipData.get(world).removeShip(physicsObject.getShipData());
                iterator.remove();
            }
        }

        // Then execute queued ship spawn operations
        spawnNewShips();

        // Then determine which ships to load and unload
        this.loadingController.determineLoadAndUnload();

        // Then execute queued ship load and unload operations
        loadAndUnloadShips();

        // Then tick all the loaded ships
        for (PhysicsObject ship : getAllLoadedPhysObj()) {
            ship.onTick();
        }

        // Finally, send the players updates about the ships.
        this.loadingController.sendUpdatesToPlayers();

        // And then update the thread safe ship list.
        this.threadSafeLoadedShips = ImmutableList.copyOf(this.loadedShips.values());
    }

    private void spawnNewShips() {
        for (final ImmutableTriple<BlockPos, ShipData, BlockFinder.BlockFinderType> spawnData : spawnQueue) {
            final BlockPos physicsInfuserPos = spawnData.getLeft();
            final ShipData toSpawn = spawnData.getMiddle();
            final BlockFinder.BlockFinderType blockBlockFinderType = spawnData.getRight();

            if (this.loadedShips.containsKey(toSpawn.getUuid())) {
                throw new IllegalStateException("Tried spawning a ShipData that was already loaded?\n" + toSpawn);
            }

            final SpatialDetector detector = BlockFinder.getBlockFinderFor(
                    blockBlockFinderType,
                    physicsInfuserPos,
                    this.world,
                    VSConfig.maxDetectedShipSize + 1,
                    true,
                    toSpawn.takeBlockBlacklist()
            );

            if (VSConfig.showAnnoyingDebugOutput) {
                System.out.println("Attempting to spawn " + toSpawn + " on the thread " + Thread.currentThread().getName());
            }
            if (detector.foundSet.size() > VSConfig.maxDetectedShipSize || detector.cleanHouse) {
                System.err.println("Ship too big or bedrock detected!");

                /*
                if (creator != null) {
                    creator.sendMessage(new TextComponentString(
                            "Ship construction canceled because its exceeding the ship size limit; "
                                    +
                                    "or because it's attached to bedrock. " +
                                    "Raise it with /physsettings maxshipsize [number]"));
                }

                 */
                continue; // Skip ship construction
            }

            // When copying the ship chunks we want to keep track of the inertia and center of mass.
            IPhysicsObjectCenterOfMassProvider centerOfMassProvider = new BasicCenterOfMassProvider();

            // Then create the ship chunks
            MutableBlockPos srcLocationPos = new MutableBlockPos();
            BlockPos centerDifference = toSpawn.getChunkClaim().getRegionCenter().subtract(physicsInfuserPos);

            MutableBlockPos pasteLocationPos = new MutableBlockPos();

            TLongObjectHashMap<Chunk> copiedChunksMap = new TLongObjectHashMap<>();
            TLongObjectHashMap<Chunk> sourceChunksMap = new TLongObjectHashMap<>();
            TLongHashSet occupiedShipChunks = new TLongHashSet();
            IBlockState airState = Blocks.AIR.getDefaultState();
            int[] detectedBlocks = detector.foundSet.toArray();

            // Relocation-aware tiles need the complete destination set before any tile is copied.
            for (int hashedPos : detectedBlocks) {
                SpatialDetector.setPosWithRespectTo(hashedPos, detector.firstBlock, srcLocationPos);
                pasteLocationPos.setPos(srcLocationPos.getX() + centerDifference.getX(), srcLocationPos.getY() + centerDifference.getY(), srcLocationPos.getZ() + centerDifference.getZ());
                if (toSpawn.blockPositions != null) toSpawn.blockPositions.add(pasteLocationPos.getX(), pasteLocationPos.getY(), pasteLocationPos.getZ());
                occupiedShipChunks.add(ChunkPos.asLong(
                    pasteLocationPos.getX() >> 4,
                    pasteLocationPos.getZ() >> 4
                ));

                int sourceChunkX = srcLocationPos.getX() >> 4;
                int sourceChunkZ = srcLocationPos.getZ() >> 4;
                long sourceChunkPos = ChunkPos.asLong(sourceChunkX, sourceChunkZ);
                Chunk sourceChunk = sourceChunksMap.get(sourceChunkPos);
                if (sourceChunk != null) continue;
                sourceChunksMap.put(sourceChunkPos, world.getChunk(sourceChunkX, sourceChunkZ));
            }

            //claim only chunks the ship occupies and a one-chunk margin for collision checks and construction
            TLongIterator occupiedChunksIterator = occupiedShipChunks.iterator();
            while (occupiedChunksIterator.hasNext()) {
                long occupiedChunk = occupiedChunksIterator.next();
                int occupiedChunkX = ChunkPos.getX(occupiedChunk);
                int occupiedChunkZ = ChunkPos.getZ(occupiedChunk);
                for (int chunkOffsetX = -1; chunkOffsetX <= 1; chunkOffsetX++) {
                    for (int chunkOffsetZ = -1; chunkOffsetZ <= 1; chunkOffsetZ++) {
                        toSpawn.getChunkClaim().addChunkClaim(
                            occupiedChunkX + chunkOffsetX,
                            occupiedChunkZ + chunkOffsetZ
                        );
                    }
                }
            }

            // First, copy the blocks and tiles to the new chunks
            for (int hashedPos : detectedBlocks) {
                SpatialDetector.setPosWithRespectTo(hashedPos, detector.firstBlock, srcLocationPos);
                // Get the BlockPos from the hashedPos
                pasteLocationPos.setPos(srcLocationPos.getX() + centerDifference.getX(), srcLocationPos.getY() + centerDifference.getY(), srcLocationPos.getZ() + centerDifference.getZ());

                // Then create a chunk to accommodate this block (if one does not already exist).
                int newChunkX = pasteLocationPos.getX() >> 4;
                int newChunkZ = pasteLocationPos.getZ() >> 4;

                long newChunkPosLong = ChunkPos.asLong(newChunkX, newChunkZ);

                Chunk newChunk = copiedChunksMap.get(newChunkPosLong);
                if (newChunk == null) {
                    newChunk = new Chunk(this.world, newChunkX, newChunkZ);
                    copiedChunksMap.put(newChunkPosLong, newChunk);
                }

                // Then copy the IBlockState & TileEntity to the new Chunk
                // Get the old world Chunk
                long sourceChunkPos = ChunkPos.asLong(
                        srcLocationPos.getX() >> 4,
                        srcLocationPos.getZ() >> 4
                );
                Chunk chunkToSet = sourceChunksMap.get(sourceChunkPos);

                // Then get the old IBlockState, as efficiently as possible
                int storageIndex = srcLocationPos.getY() >> 4;
                // Check that we're placing the block in a valid position
                if (storageIndex < 0 || storageIndex >= chunkToSet.getBlockStorageArray().length) {
                    // Invalid position, abort!
                    throw new IllegalStateException("Incorrect block copy!\n" + srcLocationPos);
                }

                IBlockState srcState = chunkToSet.getBlockStorageArray()[storageIndex]
                        .get(srcLocationPos.getX() & 15, srcLocationPos.getY() & 15, srcLocationPos.getZ() & 15);

                // Then paste that IBlockState into the new ship chunk
                int newChunkStorageIndex = pasteLocationPos.getY() >> 4;

                if (newChunk.getBlockStorageArray()[newChunkStorageIndex] == Chunk.NULL_BLOCK_STORAGE) {
                    newChunk.getBlockStorageArray()[newChunkStorageIndex] = new ExtendedBlockStorage(newChunkStorageIndex << 4, true);
                }
                newChunk.getBlockStorageArray()[newChunkStorageIndex]
                        .set(pasteLocationPos.getX() & 15, pasteLocationPos.getY() & 15, pasteLocationPos.getZ() & 15, srcState);

                // If this block is force block, then add it to the activeForcePositions list of the ship.
                if (BlockPhysicsDetails.isBlockProvidingForce(srcState) && toSpawn.activeForcePositions != null) {
                    toSpawn.activeForcePositions.add(pasteLocationPos);
                }

                // Also update the center of mass and inertia provider
                centerOfMassProvider.onSetBlockState(toSpawn.getInertiaData(), pasteLocationPos,
                        airState, srcState);

                // Then copy the TileEntity (if there is one)
                TileEntity srcTile = this.world.getTileEntity(srcLocationPos);
                if (srcTile != null) {
                    TileEntity pasteTile;
                    if (srcTile instanceof IRelocationAwareTile relocationAwareTile) {
                        pasteTile = relocationAwareTile.createRelocatedTile(pasteLocationPos, toSpawn);
                    }
                    else {
                        NBTTagCompound tileEntNBT = srcTile.writeToNBT(new NBTTagCompound());
                        // Change the block position to be inside of the Ship
                        tileEntNBT.setInteger("x", pasteLocationPos.getX());
                        tileEntNBT.setInteger("y", pasteLocationPos.getY());
                        tileEntNBT.setInteger("z", pasteLocationPos.getZ());
                        pasteTile = TileEntity.create(this.world, tileEntNBT);
                    }

                    // Finally, add the new TileEntity to the new ship chunk.
                    newChunk.addTileEntity(pasteTile);
                }
            }

            preserveInfuserPlacementAfterCenterOfMassChange(toSpawn);

            // Then delete the copied blocks from the old chunks
            for (int hashedPos : detectedBlocks) {
                SpatialDetector.setPosWithRespectTo(hashedPos, detector.firstBlock, srcLocationPos);

                long sourceChunkPos = ChunkPos.asLong(
                        srcLocationPos.getX() >> 4,
                        srcLocationPos.getZ() >> 4
                );
                Chunk chunkToSet = sourceChunksMap.get(sourceChunkPos);

                // Then get the old IBlockState, as efficiently as possible
                int storageIndex = srcLocationPos.getY() >> 4;
                // Check that we're placing the block in a valid position
                if (storageIndex < 0 || storageIndex >= chunkToSet.getBlockStorageArray().length) {
                    // Invalid position, abort!
                    throw new IllegalStateException("Incorrect block copy!\n" + srcLocationPos);
                }

                IBlockState srcState = chunkToSet.getBlockStorageArray()[storageIndex]
                        .get(srcLocationPos.getX() & 15, srcLocationPos.getY() & 15,
                                srcLocationPos.getZ() & 15);

                // THIS IS TEMP because its extremely inefficient.
                // Come up with a clever way to let clients figure this out in the future.
                this.world.notifyBlockUpdate(srcLocationPos, srcState, airState, 3);
                this.world.notifyNeighborsOfStateChange(srcLocationPos, Blocks.AIR, false);

                // Finally, delete the old IBlockState and TileEntity from the old Chunk
                chunkToSet.getBlockStorageArray()[storageIndex]
                        .set(srcLocationPos.getX() & 15, srcLocationPos.getY() & 15, srcLocationPos.getZ() & 15, airState);
                if (Loader.isModLoaded("malisiscore")) {
                    MalisisCoreCompat.updateChunkBlockRegistration(this.world, srcLocationPos, srcState, airState);
                }

                // Delete the TileEntity at this pos (if there is one)
                this.world.removeTileEntity(srcLocationPos);
            }

            // Then relight the original chunks
            for (Chunk sourceChunk : sourceChunksMap.valueCollection()) {
                sourceChunk.generateSkylightMap();
                sourceChunk.checkLight();
                sourceChunk.markDirty();
            }

            // Then inject the ship chunks into the world
            toSpawn.getChunkClaim().forEach((x, z) -> {
                long chunkLong = ChunkPos.asLong(x, z);
                Chunk copiedChunk = copiedChunksMap.get(chunkLong);
                if (copiedChunk == null) copiedChunk = new Chunk(this.world, x, z);
                injectChunkIntoWorldServer(copiedChunk, x, z);
            });

            // relight only after the whole claim, including its one-chunk margin, has been installed in the world.
            for (ChunkPos chunkPos : toSpawn.getChunkClaim()) {
                Chunk chunk = this.world.getChunk(chunkPos.x, chunkPos.z);
                chunk.generateSkylightMap();
                chunk.checkLight();
                chunk.markDirty();
            }

            //transfer lighting properly when creating ship
            if (toSpawn.blockPositions != null) {
                for (BlockPos blockPos : toSpawn.blockPositions) {
                    IBlockState blockState = this.world.getBlockState(blockPos);
                    if (blockState.getLightValue(this.world, blockPos) > 0) {
                        this.world.checkLightFor(EnumSkyBlock.BLOCK, blockPos);
                    }
                }
            }

            // Add shipData to the ShipData storage
            QueryableShipData.get(this.world).addShip(toSpawn);

            // Finally, instantiate the PhysicsObject representation of this ShipData
            PhysicsObject physicsObject = new PhysicsObject(this.world, toSpawn);
            this.loadedShips.put(toSpawn.getUuid(), physicsObject);

            // Fairy Lights initially builds connections before the ship can be resolved. Rebuild
            // them now so their artificial block light is written into the installed ship chunks.
            if (Loader.isModLoaded("fairylights")) {
                FairyLightsCompat.rebuildShipLighting(physicsObject);
            }
        }
        this.spawnQueue.clear();
    }

    private static void preserveInfuserPlacementAfterCenterOfMassChange(ShipData shipData) {
        ShipTransform originalTransform = shipData.getShipTransform();
        org.joml.Vector3dc oldCenter = originalTransform.getCenterCoord();
        org.joml.Vector3dc newCenter = shipData.getInertiaData().getGameTickCenterOfMass();
        org.joml.Vector3d positionOffset = newCenter.sub(oldCenter, new org.joml.Vector3d());
        ShipTransform correctedTransform = new ShipTransform(
            originalTransform.getPosX() + positionOffset.x,
            originalTransform.getPosY() + positionOffset.y,
            originalTransform.getPosZ() + positionOffset.z,
            originalTransform.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL),
            newCenter
        );
        shipData.setShipTransform(correctedTransform);
        shipData.setPrevTickShipTransform(correctedTransform);
        shipData.getPhysicsData().markCollisionShapeDirty();
    }

    private void injectChunkIntoWorldServer(@Nonnull Chunk chunk, int x, int z) {
        ChunkProviderServer provider = this.world.getChunkProvider();
        provider.loadedChunks.put(ChunkPos.asLong(x, z), chunk);
        chunk.onLoad();
        chunk.markDirty();
    }

    private void loadAndUnloadShips() {
        QueryableShipData queryableShipData = QueryableShipData.get(this.world);
        // Load the ships that are required immediately.
        for (final UUID toLoadID : loadQueue) {
            Optional<ShipData> toLoadOptional = queryableShipData.getShip(toLoadID);
            if (toLoadOptional.isEmpty()) {
                throw new IllegalStateException("No ship found for ID:\n" + toLoadID);
            }
            ShipData toLoad = toLoadOptional.get();
            if (loadedShips.containsKey(toLoadID)) {
                throw new IllegalStateException("Tried loading a ShipData that was already loaded?\n" + toLoad);
            }

            // Remove this ship from the background loading set, if it is in it.
            loadingInBackground.remove(toLoadID);
            // Finally, load the ship.
            if (VSConfig.showAnnoyingDebugOutput) {
                System.out.println("Attempting to load ship " + toLoad);
            }
            PhysicsObject physicsObject = new PhysicsObject(this.world, toLoad);
            PhysicsObject old = loadedShips.put(toLoad.getUuid(), physicsObject);
            if (old != null) {
                throw new IllegalStateException("How did we already have a ship loaded for " + toLoad);
            }
        }
        this.loadQueue.clear();

        // Load ships that aren't required immediately in the background.
        for (final UUID toLoadID : this.backgroundLoadQueue) {
            // Skip if this ship is already being loaded in the background,.
            if (this.loadingInBackground.contains(toLoadID)) {
                continue; // Already loading this ship in the background
            }

            // Make sure there isn't an already loaded ship with this UUID.
            if (this.loadedShips.containsKey(toLoadID)) {
                // continue; // temp, need to fix WorldShipLoadingController.determineLoadAndUnload()
                throw new IllegalStateException("Tried loading a ShipData that was already loaded? Ship ID is\n"
                        + toLoadID);
            }
            // Then try getting the ShipData for this UUID.
            Optional<ShipData> toLoadOptional = queryableShipData.getShip(toLoadID);
            if (toLoadOptional.isEmpty()) {
                throw new IllegalStateException("No ship found for ID:\n" + toLoadID);
            }

            ShipData toLoad = toLoadOptional.get();
            this.loadingInBackground.add(toLoadID);

            if (VSConfig.showAnnoyingDebugOutput) {
                System.out.println("Attempting to load " + toLoad + " in the background.");
            }
            ChunkProviderServer chunkProviderServer = this.world.getChunkProvider();
            for (ChunkPos chunkPos : toLoad.getChunkClaim()) {
                @Nonnull Runnable returnTask = () -> {
                    if (VSConfig.showAnnoyingDebugOutput) {
                        System.out.println("Loaded ship chunk " + chunkPos);
                    }
                };
                chunkProviderServer.loadChunk(chunkPos.x, chunkPos.z, returnTask);
            }
        }
        this.backgroundLoadQueue.clear();

        // Unload far away ships immediately.
        for (final UUID toUnloadID : this.unloadQueue) {
            // Make sure we have a ship with this ID that can be unloaded
            if (!this.loadedShips.containsKey(toUnloadID)) {
                throw new IllegalStateException("Tried unloading a ShipData that isn't loaded? Ship ID is\n"
                        + toUnloadID);
            }

            PhysicsObject physicsObject = getPhysObjectFromUUID(toUnloadID);

            if (VSConfig.showAnnoyingDebugOutput) {
                System.out.println("Attempting to unload " + physicsObject);
            }
            physicsObject.unload();
            boolean success = this.loadedShips.remove(toUnloadID, physicsObject);

            if (!success) {
                throw new IllegalStateException("How did we fail to unload " + physicsObject.getShipData());
            }
        }
        this.unloadQueue.clear();
    }

    @Nonnull
    @Override
    public Iterable<PhysicsObject> getAllLoadedPhysObj() throws CalledFromWrongThreadException {
        if (this.world.isCallingFromMinecraftThread()) {
            return this.loadedShips.values();
        }
        return this.threadSafeLoadedShips;
    }

    @Nonnull
    @Override
    public ImmutableList<PhysicsObject> getAllLoadedThreadSafe() {
        return threadSafeLoadedShips;
    }

    /**
     * Thread safe way to queue a ship spawn. (Not the same as {@link #queueShipLoad(UUID)}.
     */
    public void queueShipSpawn(@Nonnull ShipData data, @Nonnull BlockPos spawnPos, @Nonnull BlockFinder.BlockFinderType blockFinderType) {
        this.enforceGameThread();
        this.spawnQueue.add(ImmutableTriple.of(spawnPos, data, blockFinderType));
    }

    @Override
    public void queueShipLoad(@Nonnull UUID shipID) {
        this.enforceGameThread();
        this.loadQueue.add(shipID);
    }

    @Override
    public void queueShipUnload(@Nonnull UUID shipID) {
        this.enforceGameThread();
        this.unloadQueue.add(shipID);
    }

    /**
     * Thread safe way to queue a ship to be loaded in the background.
     */
    public void queueShipLoadBackground(@Nonnull UUID shipID) {
        this.enforceGameThread();
        backgroundLoadQueue.add(shipID);
    }

    /**
     * Used to prevent the world from unloading the chunks of ships loading in background.
     */
    public Iterable<Long> getBackgroundShipChunks() throws CalledFromWrongThreadException {
        this.enforceGameThread();
        List<Long> backgroundChunks = new ArrayList<>();
        QueryableShipData queryableShipData = QueryableShipData.get(this.world);
        for (UUID shipID : this.loadingInBackground) {
            Optional<ShipData> shipDataOptional = queryableShipData.getShip(shipID);
            if (shipDataOptional.isEmpty()) {
                throw new IllegalStateException("Ship data not present for:\n" + shipID);
            }
            backgroundChunks.addAll(shipDataOptional.get().getChunkClaim().getClaimedChunks());
        }
        return backgroundChunks;
    }

    @Override
    @NonNull
    public WorldServer getWorld() {
        return this.world;
    }

    public VSWorldPhysicsLoop getPhysicsLoop() {
        return this.physicsLoop;
    }
}
