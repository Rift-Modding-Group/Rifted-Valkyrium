package org.valkyrienskies.mod.common.ships.ship_world;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.util.multithreaded.CalledFromWrongThreadException;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;

public class WorldClientShipManager implements IPhysObjectWorld {

    private final World world;
    private final Map<UUID, PhysicsObject> loadedShips;
    // Use LinkedHashSet as a queue because it preserves order and doesn't allow duplicates
    private final LinkedHashSet<UUID> loadQueue, unloadQueue;
    private volatile ImmutableList<PhysicsObject> threadSafeLoadedShips;

    public WorldClientShipManager(World world) {
        this.world = world;
        this.loadedShips = new HashMap<>();
        this.loadQueue = new LinkedHashSet<>();
        this.unloadQueue = new LinkedHashSet<>();
        this.threadSafeLoadedShips = ImmutableList.of();
    }

    private void enforceGameThread() throws CalledFromWrongThreadException {
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            //throw new CalledFromWrongThreadException("Wrong thread calling code: " + Thread.currentThread());
            ValkyrienSkiesMod.LOGGER.warn("Wrong thread calling code: " + Thread.currentThread());
        }
    }

    @Override
    public void tick() {
        loadAndUnloadShips();

        for (PhysicsObject physicsObject : getAllLoadedPhysObj()) {
            physicsObject.onTick();
        }

        // Update the thread safe ship list.
        this.threadSafeLoadedShips = ImmutableList.copyOf(this.loadedShips.values());
    }

    private void loadAndUnloadShips() {
        QueryableShipData queryableShipData = QueryableShipData.get(this.world);
        // Load ships queued for loading
        for (final UUID toLoadID : this.loadQueue) {
            if (this.loadedShips.containsKey(toLoadID)) {
                ValkyrienSkiesMod.LOGGER.error("Tried loading a for ship that was already loaded? UUID is\n" + toLoadID);
                continue;
            }
            Optional<ShipData> toLoadOptional = queryableShipData.getShip(toLoadID);
            if (toLoadOptional.isEmpty()) {
                ValkyrienSkiesMod.LOGGER.error("No ship found for UUID:\n" + toLoadID);
                continue;
            }
            ShipData shipData = toLoadOptional.get();

            PhysicsObject physicsObject = new PhysicsObject(this.world, shipData);

            for (final Chunk chunk : physicsObject.getClaimedChunkCache()) {
                chunk.loaded = true;
            }

            this.loadedShips.put(toLoadID, physicsObject);
            if (VSConfig.showAnnoyingDebugOutput) {
                System.out.println("Successfully loaded " + shipData);
            }
        }
        this.loadQueue.clear();

        // Unload ships queued for unloading
        for (final UUID toUnloadID : this.unloadQueue) {
            if (!this.loadedShips.containsKey(toUnloadID)) {
                ValkyrienSkiesMod.LOGGER.error("Tried unloading that isn't loaded? ID is\n" + toUnloadID);
                continue;
            }
            PhysicsObject removedShip = this.loadedShips.get(toUnloadID);
            removedShip.unload();
            this.loadedShips.remove(toUnloadID);
            if (VSConfig.showAnnoyingDebugOutput) {
                System.out.println("Successfully unloaded " + removedShip.getShipData());
            }
        }
        this.unloadQueue.clear();
    }

    @Override
    public void onWorldUnload() {
        this.loadedShips.clear();
        this.threadSafeLoadedShips = ImmutableList.of();
    }

    @Nullable
    @Override
    public PhysicsObject getPhysObjectFromUUID(@Nonnull UUID shipID) throws CalledFromWrongThreadException {
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
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
        for (PhysicsObject physicsObject : getAllLoadedPhysObj()) {
            if (toCheck.intersects(physicsObject.getShipBB())) {
                nearby.add(physicsObject);
            }
        }
        return nearby;
    }

    @Nonnull
    @Override
    public Iterable<PhysicsObject> getAllLoadedPhysObj() throws CalledFromWrongThreadException {
        if (Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
            return this.loadedShips.values();
        }
        return threadSafeLoadedShips;
    }

    @Nonnull
    @Override
    public ImmutableList<PhysicsObject> getAllLoadedThreadSafe() {
        return this.threadSafeLoadedShips;
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

    @Nonnull
    @Override
    public World getWorld() {
        return this.world;
    }
}
