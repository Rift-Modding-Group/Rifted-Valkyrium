package org.valkyrienskies.mod.common.util.multithreaded;

import com.google.common.collect.ImmutableList;
import net.minecraft.client.Minecraft;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.ship_world.IShipWorld;
import org.valkyrienskies.mod.common.physics.PhysXWorldBackend;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.network.ShipTransformUpdateMessage;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.*;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Handles the physics for a given world. This is run on a separate thread, not on the game tick.
 */
public class VSWorldPhysicsLoop implements Runnable {
    // The number of physics ticks to be considered in the average tick time.
    private final static long TICK_TIME_QUEUE = 100;
    // Used to give each VS thread a unique name
    private static int worldPhysicsLoopId = 0;
    @NotNull
    private final World hostWorld;
    //the heart and soul of the physics used by this mod
    @NotNull
    private final PhysXWorldBackend physXBackend;
    private long lastPacketSendTime = 0;
    // The ships we will be ticking physics for every tick, and sending those
    // updates to players.
    // Used by the game thread to mark this thread for death.
    private volatile boolean threadRunning;

    private final Queue<Runnable> taskQueue;
    private ImmutableList<PhysicsObject> immutableShipsList;
    private final ConcurrentLinkedQueue<IPhysTimeTask> recurringTasks;

    private final String name;

    private final Queue<Long> latestPhysicsTickTimes;

    public VSWorldPhysicsLoop(@NotNull World host) {
        this.name = "VS World Physics Task " + worldPhysicsLoopId;
        worldPhysicsLoopId++;
        this.hostWorld = host;
        this.physXBackend = new PhysXWorldBackend();
        this.threadRunning = true;
        this.latestPhysicsTickTimes = new ConcurrentLinkedQueue<>();
        this.taskQueue = new ConcurrentLinkedQueue<>();
        this.immutableShipsList = ImmutableList.of();
        this.recurringTasks = new ConcurrentLinkedQueue<>();
        ValkyrienSkiesMod.LOGGER.trace(this.name + " created.");
    }

    @SideOnly(Side.CLIENT)
    private static boolean isSinglePlayerPaused() {
        return Minecraft.getMinecraft().isGamePaused();
    }

    public void addScheduledTask(Runnable r) {
        this.taskQueue.add(r);
    }

    private static long getNsPerTick() {
        return (long) (1_000_000_000 / VSConfig.targetTps);
    }

    public void addRecurringTask(IPhysTimeTask physTask) {
        this.recurringTasks.add(physTask);
    }

    @Override
    public void run() {
        try {
            while (this.threadRunning) {
                final MinecraftServer mcServer = this.hostWorld.getMinecraftServer();
                assert mcServer != null;
                // If server then always tick physics, if single-player then only tick when not paused.
                final boolean tickPhysics = mcServer.isServerRunning() && (mcServer.isDedicatedServer() || !isSinglePlayerPaused());

                if (tickPhysics) {
                    // The number of seconds the physics engine will move forward
                    final double timeToSimulate = VSConfig.getTimeSimulatedPerTick();
                    // The number of nanoseconds we want our physics engine tick to take
                    final long idealTickTime = (long) (1E9 / VSConfig.targetTps);

                    final long physTickStartTime = System.nanoTime();
                    // Run the physics engine tick
                    this.physicsTick(timeToSimulate);
                    final long physTickEndTime = System.nanoTime();
                    final long physTickDuration = physTickEndTime - physTickStartTime;

                    // If the physics tick ran faster than the ideal tick time, then pretend it took the ideal tick time by
                    // waiting.
                    if (physTickDuration < idealTickTime) {
                        final long sleepMillis = (idealTickTime - physTickDuration) / 1_000_000L;
                        try {
                            Thread.sleep(sleepMillis);
                        }
                        catch (InterruptedException e) {
                            if (this.threadRunning) e.printStackTrace();
                            Thread.currentThread().interrupt();
                        }
                    }

                    // Keep track of the time it took to run the physics tick, including the time we spent sleeping.
                    final long physTickDurationIncludingSleep = System.nanoTime() - physTickStartTime;
                    this.latestPhysicsTickTimes.add(physTickDurationIncludingSleep);
                    // Ensure that latestPhysicsTickTimes only has TICK_TIME_QUEUE # of elements
                    if (this.latestPhysicsTickTimes.size() > TICK_TIME_QUEUE) {
                        this.latestPhysicsTickTimes.remove();
                    }
                }
                else {
                    // If physics are disabled then sleep for 100 ms.
                    // If we don't sleep then we waste a ton of CPU just being in this while(true) loop.
                    try {
                        Thread.sleep(100L);
                    }
                    catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
        finally {
            this.physXBackend.close();
            // If we get to this point of run(), then we are about to return and this thread
            // will terminate soon.
            ValkyrienSkiesMod.LOGGER.trace(this.name + " killed");
        }
    }

    private void physicsTick(double delta) {
        IShipWorld shipWorld = this.hostWorld.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld == null) return;

        // Update the immutable ship list.
        this.immutableShipsList = shipWorld.getManager().getAllLoadedThreadSafe();

        // Run tasks queued to run on physics thread
        this.recurringTasks.forEach(t -> t.runTask(delta));
        this.taskQueue.forEach(Runnable::run);
        this.taskQueue.clear();

        // Make a sublist of physics objects to process physics on.
        List<PhysicsObject> physicsEntitiesToDoPhysics = new ArrayList<>();
        for (PhysicsObject physicsObject : this.immutableShipsList) {
            if (physicsObject.isPhysicsReady() && physicsObject.isPhysicsEnabled() && physicsObject.getCachedSurroundingChunks() != null) {
                physicsEntitiesToDoPhysics.add(physicsObject);
            }
        }

        // Finally, actually process the physics tick
        this.tickThePhysicsAndCollision(physicsEntitiesToDoPhysics, delta);

        // Send ship position update packets around 20 times a second
        final long currentTimeMillis = System.currentTimeMillis();
        final double secondsSinceLastPacket = (currentTimeMillis - this.lastPacketSendTime) / 1000D;

        // Use 0.04 to guarantee we're always sending at least 20 packets per second
        if (secondsSinceLastPacket > 0.04) {
            // Update the last update time
            this.lastPacketSendTime = currentTimeMillis;

            try {
                // At the end, send the transform update packets
                final ShipTransformUpdateMessage shipTransformUpdateMessage = new ShipTransformUpdateMessage();
                final int dimensionID = this.hostWorld.provider.getDimension();

                shipTransformUpdateMessage.setDimensionID(dimensionID);
                for (final PhysicsObject physicsObject : this.immutableShipsList) {
                    final UUID shipUUID = physicsObject.getUuid();
                    final ShipTransform shipTransform = physicsObject.getShipTransformationManager().getCurrentPhysicsTransform();
                    final AxisAlignedBB shipBB = physicsObject.getPhysicsTransformAABB();

                    shipTransformUpdateMessage.addData(shipUUID, shipTransform, shipBB);
                }
                ValkyrienSkiesMod.physWrapperTransformUpdateNetwork.sendToDimension(shipTransformUpdateMessage, shipTransformUpdateMessage.getDimensionID());
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * Ticks ship physics and collision through PhysX.
     */
    private void tickThePhysicsAndCollision(List<PhysicsObject> shipsWithPhysics, double timeStep) {
        this.physXBackend.update(this.hostWorld, shipsWithPhysics, timeStep);
    }

    /**
     * Marks this physics thread for death. Doesn't immediately end the thread, but instead ensures
     * the thread will die after the current running physics tick is finished.
     */
    public void kill() {
        ValkyrienSkiesMod.LOGGER.trace(name + " marked for death.");
        this.threadRunning = false;
    }

    /**
     * @return The average runtime of the last 100 physics ticks in nanoseconds.
     */
    public long getAveragePhysicsTickTimeNano() {
        if (this.latestPhysicsTickTimes.size() >= TICK_TIME_QUEUE) {
            long average = 0;
            for (Long tickTime : this.latestPhysicsTickTimes) average += tickTime;
            return average / TICK_TIME_QUEUE;
        }
        // If we don't have enough data to get an average, just assume its the ideal
        // tick time.
        return getNsPerTick();
    }

    public String getName() {
        return this.name;
    }
}
