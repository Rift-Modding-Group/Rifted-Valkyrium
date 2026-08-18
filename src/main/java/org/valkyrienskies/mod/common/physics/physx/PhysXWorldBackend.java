package org.valkyrienskies.mod.common.physics.physx;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.AbstractPhysicsBackend;
import org.valkyrienskies.mod.common.physics.BlockSection;
import org.valkyrienskies.mod.common.physics.PhysicsCollideWith;
import org.valkyrienskies.mod.common.physics.PhysicsEntityMovementQueue;
import org.valkyrienskies.mod.common.physics.PhysicsEntitySnapshot;
import org.valkyrienskies.mod.common.physics.physx.bodies.AbstractPhysXCollisionObject;
import org.valkyrienskies.mod.common.physics.physx.bodies.PhysXBlockSectionBody;
import org.valkyrienskies.mod.common.physics.physx.bodies.PhysXEntityBody;
import org.valkyrienskies.mod.common.physics.physx.bodies.PhysXShipBody;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import physx.PxTopLevelFunctions;
import physx.common.PxVec3;
import physx.physics.PxMaterial;
import physx.physics.PxPhysics;
import physx.physics.PxScene;
import physx.physics.PxSceneDesc;
import physx.physics.PxSceneFlagEnum;

import java.util.*;

/**
 * One PhysX scene for a loaded Minecraft dimension.
 * VS ship blocks remain in their shipyard chunks. This backend creates projected
 * PhysX actors for those ships and for nearby blocks, liquids, and entities.
 */
public class PhysXWorldBackend extends AbstractPhysicsBackend {
    @NotNull
    private final PhysXRuntime runtime;
    @NotNull
    private final PhysicsEntityMovementQueue physicsEntityMovementQueue;
    @NotNull
    private final PxPhysics physics;
    @NotNull
    private final PxScene scene;
    //common physX materials for each collision object
    @NotNull
    private final EnumMap<PhysXActor, PxMaterial> materials = new EnumMap<>(PhysXActor.class);
    //list of collision objects in the entire world
    @NotNull
    private final Map<CollisionBodyType, Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject<?>>> collisionObjects = CollisionBodyType.createRegistry();
    //separate list of block section objects with liquids to deal with ship buoyancy
    @NotNull
    private final List<PhysXBlockSectionBody> blockSectionsWithLiquids = new ArrayList<>();
    //helper to track last sync generation each collision object was seen in, so stale collision objects can be released
    private final TObjectIntMap<AbstractPhysXCollisionObject.Identifier> collisionObjectSyncGenerations = new TObjectIntHashMap<>();
    //current sync generation
    private int syncGeneration;
    private boolean closed;

    public PhysXWorldBackend(@NotNull PhysicsEntityMovementQueue physicsEntityMovementQueue) {
        this.physicsEntityMovementQueue = physicsEntityMovementQueue;
        this.runtime = PhysXRuntime.acquire();
        this.physics = this.runtime.physics;

        //init gravity
        PxVec3 gravityVec = new PxVec3(
            VSConfig.doGravity ? (float) VSConfig.gravityVecX : 0f,
            VSConfig.doGravity ? (float) VSConfig.gravityVecY : 0f,
            VSConfig.doGravity ? (float) VSConfig.gravityVecZ : 0f
        );

        //init scene
        PxSceneDesc sceneDesc = new PxSceneDesc(this.runtime.tolerances);
        sceneDesc.setGravity(gravityVec);
        sceneDesc.setCpuDispatcher(this.runtime.cpuDispatcher);
        sceneDesc.setFilterShader(PxTopLevelFunctions.DefaultFilterShader());
        this.scene = this.physics.createScene(sceneDesc);
        this.scene.setFlag(PxSceneFlagEnum.eENABLE_CCD, true);
        this.scene.setFlag(PxSceneFlagEnum.eENABLE_STABILIZATION, true);
        this.scene.setFlag(PxSceneFlagEnum.eENABLE_FRICTION_EVERY_ITERATION, true);

        //init materials
        for (PhysXActor actor : PhysXActor.values()) {
            this.materials.put(actor, actor.createMaterial(this.physics));
        }

        //destroy temp variables
        gravityVec.destroy();
        sceneDesc.destroy();
    }

    /**
     * This is for updating the backend (and all physics stuff) from VSWorldPhysicsLoop.
     * */
    @Override
    public synchronized void update(@NotNull World hostWorld, @NotNull Collection<PhysicsObject> shipsWithPhysics, double timeStep) {
        if (this.closed) return;

        this.syncCollisionObjects(shipsWithPhysics);
        this.updateCollisionObjectsBeforeSimulation(hostWorld, timeStep);

        if (this.scene.simulate((float) timeStep)) this.scene.fetchResults(true);

        this.updateCollisionObjectsAfterSimulation();
    }

    /**
     * For updating list of collision objects from the world. These objects
     * get auto added to the scene in their constructor btw.
     * */
    private void syncCollisionObjects(@NotNull Collection<PhysicsObject> shipsWithPhysics) {
        this.advanceSyncGeneration();
        Map<Entity, PhysicsEntitySnapshot> entitySnapshots = new IdentityHashMap<>();

        //-----loop over all provided ships with physics to create collision objects-----
        for (PhysicsObject ship : shipsWithPhysics) {
            //---ship objects---
            PhysXShipBody.Identifier shipIdentifier = new PhysXShipBody.Identifier(ship);
            this.markCollisionObjectSynced(shipIdentifier);
            AbstractPhysXCollisionObject<?> shipCollisionObject = CollisionBodyType.SHIP.get(this.collisionObjects, shipIdentifier);

            //add if no ship body
            if (shipCollisionObject == null) {
                PhysXShipBody shipBody = new PhysXShipBody(shipIdentifier, this.physics, this.scene, this.getMaterial(PhysXActor.SHIP), ship);
                this.addCollisionObject(CollisionBodyType.SHIP, shipBody);
            }
            //update ship reference if there is
            else ((PhysXShipBody) shipCollisionObject).updateShipReference(ship);

            //---defining block section bodies and entity snapshots---
            PhysicsCollideWith.Snapshot collideWithSnapshot = ship.getPhysicsCollideWith().createSnapshot();
            List<PhysicsEntitySnapshot> entities = collideWithSnapshot.entities();
            List<BlockSection> blockSections = collideWithSnapshot.blockSections();

            //---block section objects---
            for (BlockSection blockSection : blockSections) {
                PhysXBlockSectionBody.Identifier sectionIdentifier = new PhysXBlockSectionBody.Identifier(
                        blockSection.world(),
                        blockSection.sectionX(),
                        blockSection.sectionY(),
                        blockSection.sectionZ(),
                        blockSection.contentsHash()
                );
                this.markCollisionObjectSynced(sectionIdentifier);
                if (CollisionBodyType.BLOCK_SECTION.get(this.collisionObjects, sectionIdentifier) == null) {
                    PhysXBlockSectionBody sectionCollider = new PhysXBlockSectionBody(
                            sectionIdentifier,
                            this.physics,
                            this.scene,
                            blockSection.world(),
                            this.getMaterial(PhysXActor.SOLID),
                            this.getMaterial(PhysXActor.LIQUID),
                            blockSection.blocks()
                    );
                    this.addCollisionObject(CollisionBodyType.BLOCK_SECTION, sectionCollider);
                }
            }

            //---collect entity snapshots on ship---
            for (PhysicsEntitySnapshot entitySnapshot : entities) {
                Entity entity = entitySnapshot.entity();
                entitySnapshots.merge(entity, entitySnapshot, (current, candidate) -> {
                    return current.supportingShip() == null && candidate.supportingShip() != null ? candidate : current;
                });
            }
        }

        //---turn entity snapshots into entity objects---
        for (PhysicsEntitySnapshot entitySnapshot : entitySnapshots.values()) {
            Entity entity = entitySnapshot.entity();
            PhysXEntityBody.Identifier entityIdentifier = new PhysXEntityBody.Identifier(entity);
            this.markCollisionObjectSynced(entityIdentifier);
            AbstractPhysXCollisionObject<?> entityCollisionObject = CollisionBodyType.ENTITY.get(this.collisionObjects, entityIdentifier);
            PhysXEntityBody entityBody;
            if (entityCollisionObject == null) {
                entityBody = new PhysXEntityBody(
                        entityIdentifier,
                        this.physics,
                        this.scene,
                        this.getMaterial(PhysXActor.ENTITY),
                        this.physicsEntityMovementQueue,
                        entitySnapshot
                );
                this.addCollisionObject(CollisionBodyType.ENTITY, entityBody);
            }
            else {
                entityBody = (PhysXEntityBody) entityCollisionObject;
                entityBody.updateEntitySnapshot(entitySnapshot);
            }

            PhysXShipBody supportingShipBody = null;
            if (entitySnapshot.supportingShip() != null) {
                AbstractPhysXCollisionObject<?> supportCollisionObject = CollisionBodyType.SHIP.get(this.collisionObjects, new PhysXShipBody.Identifier(entitySnapshot.supportingShip()));
                if (supportCollisionObject instanceof PhysXShipBody body) {
                    supportingShipBody = body;
                }
            }
            entityBody.updateSupportState(entitySnapshot, supportingShipBody);
        }

        //-----remove collision objects we do not care about anymore-----
        for (Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject<?>> bodies : this.collisionObjects.values()) {
            Iterator<Map.Entry<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject<?>>> iterator = bodies.entrySet().iterator();
            while (iterator.hasNext()) {
                Map.Entry<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject<?>> entry = iterator.next();
                AbstractPhysXCollisionObject.Identifier identifier = entry.getKey();
                if (this.collisionObjectSyncGenerations.get(identifier) == this.syncGeneration) {
                    continue;
                }

                entry.getValue().release();
                iterator.remove();
                this.collisionObjectSyncGenerations.remove(identifier);
            }
        }

        //-----rebuild list of block sections with liquids-----
        this.blockSectionsWithLiquids.clear();
        for (AbstractPhysXCollisionObject<?> collisionObject : CollisionBodyType.BLOCK_SECTION.getBodies(this.collisionObjects).values()) {
            PhysXBlockSectionBody blockSectionObject = (PhysXBlockSectionBody) collisionObject;
            if (blockSectionObject.hasLiquidBlocks()) this.blockSectionsWithLiquids.add(blockSectionObject);
        }
    }

    private void addCollisionObject(@NotNull CollisionBodyType bodyType, @NotNull AbstractPhysXCollisionObject<?> collisionObject) {
        bodyType.add(this.collisionObjects, collisionObject);
        this.markCollisionObjectSynced(collisionObject.getIdentifier());
    }

    private void markCollisionObjectSynced(AbstractPhysXCollisionObject.Identifier identifier) {
        this.collisionObjectSyncGenerations.put(identifier, this.syncGeneration);
    }

    @NotNull
    private PxMaterial getMaterial(@NotNull PhysXActor actor) {
        PxMaterial pxMaterial = this.materials.get(actor);
        if (pxMaterial == null) {
            throw new IllegalStateException("Missing PhysX material for " + actor);
        }
        return pxMaterial;
    }

    /**
     * Starts a new collision-object sync pass. Objects touched during the pass are
     * marked with the new generation; anything still carrying an older generation
     * after the pass is stale and will be released from the PhysX scene.
     * If the int counter wraps to zero, discard all old marks and restart at one,
     * since zero is the map's default value for identifiers that were never marked.
     */
    private void advanceSyncGeneration() {
        this.syncGeneration++;
        if (this.syncGeneration != 0) return;

        this.collisionObjectSyncGenerations.clear();
        this.syncGeneration = 1;
    }

    /**
     * Do I have to explain what this shit does?
     * */
    private void updateCollisionObjectsBeforeSimulation(@NotNull World hostWorld, double timeStep) {
        // Ship actors must receive their current game pose before an entity records
        // the supporting actor's pre-simulation pose.
        for (AbstractPhysXCollisionObject<?> collisionObject : CollisionBodyType.SHIP.getBodies(this.collisionObjects).values()) {
            ((PhysXShipBody) collisionObject).updateBeforeSimulation(hostWorld, this.blockSectionsWithLiquids, timeStep);
        }
        for (AbstractPhysXCollisionObject<?> collisionObject : CollisionBodyType.ENTITY.getBodies(this.collisionObjects).values()) {
            ((PhysXEntityBody) collisionObject).updateBeforeSimulation(timeStep);
        }
    }

    /**
     * e
     * */
    private void updateCollisionObjectsAfterSimulation() {
        // Publish every ship's final transform before resolving supported entities.
        for (AbstractPhysXCollisionObject<?> collisionObject : CollisionBodyType.SHIP.getBodies(this.collisionObjects).values()) {
            ((PhysXShipBody) collisionObject).updateAfterSimulation();
        }
        for (AbstractPhysXCollisionObject<?> collisionObject : CollisionBodyType.ENTITY.getBodies(this.collisionObjects).values()) {
            ((PhysXEntityBody) collisionObject).updateAfterSimulation();
        }
    }

    /**
     * To free up some memory, physics related stuff must be stopped
     * when the dimension using this backend is no longer loaded.
     * */
    @Override
    public synchronized void close() {
        if (this.closed) return;
        this.closed = true;

        for (Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject<?>> bodies : this.collisionObjects.values()) {
            for (AbstractPhysXCollisionObject<?> collisionObject : bodies.values()) {
                collisionObject.release();
            }
            bodies.clear();
        }
        this.collisionObjects.clear();
        this.blockSectionsWithLiquids.clear();
        this.collisionObjectSyncGenerations.clear();

        for (PxMaterial material : this.materials.values()) material.release();
        this.materials.clear();
        this.scene.release();
        this.runtime.release();
    }
}
