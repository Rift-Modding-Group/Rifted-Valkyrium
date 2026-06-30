package org.valkyrienskies.mod.common.physics.physx;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.material.MaterialLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.MutablePair;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.PhysicsCollideWith;
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
        Set<AbstractPhysXCollisionObject> syncedCollisionObjects = Collections.newSetFromMap(new IdentityHashMap<>());
        int syncedBlocks = 0;

        for (PhysicsObject ship : shipsWithPhysics) {
            //-----ship objects-----
            syncedCollisionObjects.add(this.addCollisionObject(
                    collisionObject -> collisionObject instanceof PhysXShipBody shipBody && shipBody.getShip() == ship,
                    () -> new PhysXShipBody(this.physics, this.scene, ship)
            ));

            //-----defining -----
            PhysicsCollideWith collideWith = ship.getPhysicsCollideWith();
            ChunkCache cachedChunks;
            MutablePair<BlockPos, BlockPos> cachedChunksCorners;
            List<Entity> entities;
            synchronized (collideWith) {
                cachedChunks = collideWith.getCachedChunks();
                cachedChunksCorners = collideWith.getCachedChunkCorners();
                entities = new ArrayList<>(collideWith.getEntities());
            }

            //-----block objects-----
            if (cachedChunks != null && cachedChunksCorners != null) {
                BlockPos cachedMin = cachedChunksCorners.getLeft();
                BlockPos cachedMax = cachedChunksCorners.getRight();

                BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
                blockScan: for (int x = cachedMin.getX(); x <= cachedMax.getX(); x++) {
                    for (int z = cachedMin.getZ(); z <= cachedMax.getZ(); z++) {
                        for (int y = cachedMin.getY(); y <= cachedMax.getY(); y++) {
                            if (syncedBlocks >= PhysicsCollideWith.MAX_BLOCKS) break blockScan;
                            mutablePos.setPos(x, y, z);
                            IBlockState state = cachedChunks.getBlockState(mutablePos);
                            Material material = state.getMaterial();
                            boolean liquid = state.getBlock() instanceof BlockLiquid || material instanceof MaterialLiquid;
                            if (material.equals(Material.AIR) || (!liquid && !material.blocksMovement())) continue;
                            BlockPos blockPos = mutablePos.toImmutable();
                            syncedBlocks++;
                            syncedCollisionObjects.add(this.addCollisionObject(
                                    collisionObject -> collisionObject instanceof PhysXBlockCollider blockCollider
                                            && blockCollider.getWorld() == hostWorld
                                            && blockCollider.getPos().equals(blockPos)
                                            && blockCollider.matches(state),
                                    () -> new PhysXBlockCollider(this.physics, this.scene, hostWorld, blockPos, state)
                            ));
                        }
                    }
                }
            }

            //-----entity objects-----
            for (Entity entity : entities) {
                syncedCollisionObjects.add(this.addCollisionObject(
                        collisionObject -> collisionObject instanceof PhysXEntityBody entityBody
                                && entityBody.getEntity().world == entity.world
                                && entityBody.getEntity().getEntityId() == entity.getEntityId(),
                        () -> new PhysXEntityBody(this.physics, this.scene, entity)
                ));
            }
        }

        //-----remove collision objects we do not care about anymore-----
        this.collisionObjects.removeIf(collisionObject -> {
            if (syncedCollisionObjects.contains(collisionObject)) return false;
            collisionObject.release();
            return true;
        });
    }

    /**
     * Create collision objects, takes into account whether or not they exist in this.collisionObjects
     * */
    private AbstractPhysXCollisionObject addCollisionObject(
            Predicate<AbstractPhysXCollisionObject> existingCollisionObject,
            Supplier<AbstractPhysXCollisionObject> collisionObjectFactory
    ) {
        //-----search in collision objects list-----
        for (AbstractPhysXCollisionObject collisionObject : this.collisionObjects) {
            if (existingCollisionObject.test(collisionObject)) return collisionObject;
        }

        //-----add if above loop wasnt broken by return statement-----
        AbstractPhysXCollisionObject collisionObject = collisionObjectFactory.get();
        this.collisionObjects.add(collisionObject);
        return collisionObject;
    }

    /**
     * Do I have to explain what this shit does?
     * */
    private void updateCollisionObjectsBeforeSimulation(World hostWorld, Collection<PhysicsObject> shipsWithPhysics, double timeStep) {
        for (AbstractPhysXCollisionObject collisionObject : new ArrayList<>(this.collisionObjects)) {
            try {
                collisionObject.updateBeforeSimulation(hostWorld, shipsWithPhysics, this.collisionObjects, timeStep);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * e
     * */
    private void updateCollisionObjectsAfterSimulation(World hostWorld, Collection<PhysicsObject> shipsWithPhysics, double timeStep) {
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
