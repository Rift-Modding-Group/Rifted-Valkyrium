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
    private final Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> collisionObjects = new HashMap<>();
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
        //set to serve as basis for removing unsynced objects
        Set<AbstractPhysXCollisionObject.Identifier> syncedCollisionObjects = new HashSet<>();

        for (PhysicsObject ship : shipsWithPhysics) {
            //-----ship objects-----
            PhysXShipBody.Identifier shipIdentifier = new PhysXShipBody.Identifier(ship);
            syncedCollisionObjects.add(shipIdentifier);
            AbstractPhysXCollisionObject shipCollisionObject = this.collisionObjects.get(shipIdentifier);
            //add if no ship body
            if (shipCollisionObject == null) {
                PhysXShipBody shipBody = new PhysXShipBody(this.physics, this.scene, ship);
                this.addCollisionObject(shipBody);
            }
            //update ship reference if there is
            else ((PhysXShipBody) shipCollisionObject).updateShipReference(ship);

            //-----defining block and entity stuff-----
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
                for (int x = cachedMin.getX(); x <= cachedMax.getX(); x++) {
                    for (int z = cachedMin.getZ(); z <= cachedMax.getZ(); z++) {
                        for (int y = cachedMin.getY(); y <= cachedMax.getY(); y++) {
                            mutablePos.setPos(x, y, z);
                            IBlockState state = cachedChunks.getBlockState(mutablePos);
                            Material material = state.getMaterial();
                            boolean liquid = state.getBlock() instanceof BlockLiquid || material instanceof MaterialLiquid;
                            if (material.equals(Material.AIR) || (!liquid && !material.blocksMovement())) continue;
                            BlockPos blockPos = mutablePos.toImmutable();

                            PhysXBlockCollider.Identifier blockIdentifier = new PhysXBlockCollider.Identifier(hostWorld, blockPos, state);
                            syncedCollisionObjects.add(blockIdentifier);
                            if (this.collisionObjects.get(blockIdentifier) == null) {
                                PhysXBlockCollider blockCollider = new PhysXBlockCollider(this.physics, this.scene, hostWorld, blockPos, state);
                                this.addCollisionObject(blockCollider);
                            }
                        }
                    }
                }
            }

            //-----entity objects-----
            for (Entity entity : entities) {
                PhysXEntityBody.Identifier entityIdentifier = new PhysXEntityBody.Identifier(entity);
                syncedCollisionObjects.add(entityIdentifier);
                if (this.collisionObjects.get(entityIdentifier) == null) {
                    PhysXEntityBody entityBody = new PhysXEntityBody(this.physics, this.scene, entity);
                    this.addCollisionObject(entityBody);
                }
            }
        }

        //-----remove collision objects we do not care about anymore-----
        Iterator<Map.Entry<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject>> iterator = this.collisionObjects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> entry = iterator.next();
            if (syncedCollisionObjects.contains(entry.getKey())) continue;

            AbstractPhysXCollisionObject collisionObject = entry.getValue();
            collisionObject.release();
            iterator.remove();
        }
    }

    private void addCollisionObject(AbstractPhysXCollisionObject collisionObject) {
        this.collisionObjects.put(collisionObject.getIdentifier(), collisionObject);
    }

    /**
     * Do I have to explain what this shit does?
     * */
    private void updateCollisionObjectsBeforeSimulation(World hostWorld, Collection<PhysicsObject> shipsWithPhysics, double timeStep) {
        for (AbstractPhysXCollisionObject collisionObject : new ArrayList<>(this.collisionObjects.values())) {
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
        for (AbstractPhysXCollisionObject collisionObject : new ArrayList<>(this.collisionObjects.values())) {
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

        for (AbstractPhysXCollisionObject collisionObject : this.collisionObjects.values()) {
            collisionObject.release();
        }
        this.collisionObjects.clear();

        this.scene.release();
        this.runtime.release();
    }
}
