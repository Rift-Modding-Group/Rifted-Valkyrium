package org.valkyrienskies.mod.common.physics.physx;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.physx.collision.AbstractPhysXCollisionObject;
import org.valkyrienskies.mod.common.physics.physx.collision.PhysXBlockCollider;
import org.valkyrienskies.mod.common.physics.physx.collision.PhysXEntityBody;
import org.valkyrienskies.mod.common.physics.physx.collision.PhysXShipBody;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import physx.PxTopLevelFunctions;
import physx.common.PxVec3;
import physx.physics.PxPhysics;
import physx.physics.PxScene;
import physx.physics.PxSceneDesc;
import physx.physics.PxSceneFlagEnum;

import java.util.*;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * One PhysX scene for a loaded Minecraft dimension.
 * VS ship blocks remain in their shipyard chunks. This backend creates projected
 * PhysX actors for those ships and for nearby blocks, liquid, and entities.
 */
public class PhysXWorldBackend {
    private static final int MAX_WORLD_BLOCK_ACTORS_PER_TICK = 12000;
    private static final int MAX_ENTITY_ACTORS_PER_TICK = 512;

    @NotNull
    private final PhysXRuntime runtime;
    @NotNull
    public final PxPhysics physics;
    @NotNull
    public final PxScene scene;

    private final List<AbstractPhysXCollisionObject> collisionObjects = new ArrayList<>();
    private boolean closed;

    public PhysXWorldBackend() {
        this.runtime = PhysXRuntime.acquire();
        this.physics = this.runtime.physics;

        PxVec3 gravityVec = new PxVec3(
            (float) VSConfig.gravityVecX,
            VSConfig.doGravity ? (float) VSConfig.gravityVecY : 0f,
            (float) VSConfig.gravityVecZ
        );
        PxSceneDesc sceneDesc = new PxSceneDesc(this.runtime.tolerances);
        sceneDesc.setGravity(gravityVec);
        sceneDesc.setCpuDispatcher(this.runtime.cpuDispatcher);
        sceneDesc.setFilterShader(PxTopLevelFunctions.DefaultFilterShader());
        this.scene = this.physics.createScene(sceneDesc);
        this.scene.setFlag(PxSceneFlagEnum.eENABLE_CCD, true);
        this.scene.setFlag(PxSceneFlagEnum.eENABLE_STABILIZATION, true);

        //destroy temp variables
        gravityVec.destroy();
        sceneDesc.destroy();
    }

    /**
     * This is for updating the backend (and all physics stuff) from VSWorldPhysicsLoop.
     * */
    public synchronized void update(World hostWorld, Collection<PhysicsObject> shipsWithPhysics, double timeStep) {
        if (this.closed) return;

        this.syncCollisionObjects(hostWorld, shipsWithPhysics);
        this.updateCollisionObjectsBeforeSimulation(hostWorld, shipsWithPhysics, timeStep);

        if (this.scene.simulate((float) timeStep)) this.scene.fetchResults(true);

        this.updateCollisionObjectsAfterSimulation(hostWorld, shipsWithPhysics, timeStep);
    }

    /**
     * For updating list of collision objects from the world.
     * */
    private void syncCollisionObjects(World hostWorld, Collection<PhysicsObject> shipsWithPhysics) {
        this.removeInvalidCollisionObjects(hostWorld, shipsWithPhysics);

        //sync ships
        for (PhysicsObject ship : shipsWithPhysics) {
            this.addCollisionObject(
                    collisionObject -> collisionObject instanceof PhysXShipBody shipBody && shipBody.getShip().getUuid().equals(ship.getUuid()),
                    () -> new PhysXShipBody(this.physics, this.scene, ship)
            );
        }

        //sync blocks
        int scannedBlocks = 0;
        blockScan:
        for (PhysicsObject ship : shipsWithPhysics) {
            AxisAlignedBB shipAabb = ship.getPhysicsTransformAABB();
            if (shipAabb == null) continue;
            AxisAlignedBB scan = shipAabb.grow(PhysXBlockCollider.ACTOR_SCAN_GROW);
            int minX = (int) Math.floor(scan.minX);
            int minY = Math.max(0, (int) Math.floor(scan.minY));
            int minZ = (int) Math.floor(scan.minZ);
            int maxX = (int) Math.ceil(scan.maxX);
            int maxY = Math.min(hostWorld.getHeight() - 1, (int) Math.ceil(scan.maxY));
            int maxZ = (int) Math.ceil(scan.maxZ);
            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    for (int y = minY; y <= maxY; y++) {
                        if (scannedBlocks++ > MAX_WORLD_BLOCK_ACTORS_PER_TICK) break blockScan;
                        mutablePos.setPos(x, y, z);
                        IBlockState state = hostWorld.getBlockState(mutablePos);
                        if (!PhysXBlockCollider.isCollidableWorldState(state)) {
                            continue;
                        }
                        BlockPos immutablePos = mutablePos.toImmutable();
                        this.addCollisionObject(
                                collisionObject -> collisionObject instanceof PhysXBlockCollider blockCollider
                                        && blockCollider.getWorld() == hostWorld
                                        && blockCollider.getPos().equals(immutablePos),
                                () -> new PhysXBlockCollider(this.physics, this.scene, hostWorld, immutablePos, state)
                        );
                    }
                }
            }
        }

        //sync entities
        int synced = 0;
        entityScan: for (PhysicsObject ship : shipsWithPhysics) {
            AxisAlignedBB shipAabb = ship.getPhysicsTransformAABB();
            if (shipAabb == null) continue;
            AxisAlignedBB entityScan = shipAabb.grow(PhysXEntityBody.ACTOR_SCAN_GROW);
            List<Entity> entities = hostWorld.getEntitiesWithinAABB(
                    Entity.class,
                    entityScan,
                    entity -> PhysXEntityBody.shouldCreateActor(entity, hostWorld)
            );
            for (Entity entity : entities) {
                if (synced++ > MAX_ENTITY_ACTORS_PER_TICK) break entityScan;
                this.addCollisionObject(
                        collisionObject -> collisionObject instanceof PhysXEntityBody entityBody
                                && entityBody.getEntity().world == entity.world
                                && entityBody.getEntity().getEntityId() == entity.getEntityId(),
                        () -> new PhysXEntityBody(this.physics, this.scene, entity)
                );
            }
        }
    }

    /**
     * Remove collision objects marked as invalid
     * */
    private void removeInvalidCollisionObjects(World hostWorld, Collection<PhysicsObject> shipsWithPhysics) {
        Iterator<AbstractPhysXCollisionObject> iterator = this.collisionObjects.iterator();
        while (iterator.hasNext()) {
            AbstractPhysXCollisionObject collisionObject = iterator.next();
            if (!collisionObject.isStillValid(hostWorld, shipsWithPhysics)) {
                collisionObject.release();
                iterator.remove();
            }
        }
    }

    /**
     * Create collision objects, takes into account whether or not they exist in this.collisionObjects
     * */
    private void addCollisionObject(
            Predicate<AbstractPhysXCollisionObject> existingCollisionObject,
            Supplier<AbstractPhysXCollisionObject> collisionObjectFactory
    ) {
        for (AbstractPhysXCollisionObject collisionObject : this.collisionObjects) {
            if (existingCollisionObject.test(collisionObject)) return;
        }

        AbstractPhysXCollisionObject collisionObject = collisionObjectFactory.get();
        this.collisionObjects.add(collisionObject);
    }

    private void updateCollisionObjectsBeforeSimulation(
            World hostWorld,
            Collection<PhysicsObject> shipsWithPhysics,
            double timeStep
    ) {
        //prevent that exception involving simultaneous updates of stuff in a list... what was it named again?
        for (AbstractPhysXCollisionObject collisionObject : new ArrayList<>(this.collisionObjects)) {
            try {
                collisionObject.updateBeforeSimulation(hostWorld, shipsWithPhysics, this.collisionObjects, timeStep);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void updateCollisionObjectsAfterSimulation(
            World hostWorld,
            Collection<PhysicsObject> shipsWithPhysics,
            double timeStep
    ) {
        for (AbstractPhysXCollisionObject collisionObject : new ArrayList<>(this.collisionObjects)) {
            try {
                collisionObject.updateAfterSimulation(hostWorld, shipsWithPhysics, this.collisionObjects, timeStep);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * To free up some memory, physics related stuff must be stopped
     * when the dimension using this backend is no longer loaded.
     * */
    public synchronized void close() {
        if (this.closed) return;
        this.closed = true;

        for (AbstractPhysXCollisionObject collisionObject : this.collisionObjects) {
            collisionObject.release();
        }
        this.collisionObjects.clear();

        this.scene.release();
        this.runtime.release();
    }
}
