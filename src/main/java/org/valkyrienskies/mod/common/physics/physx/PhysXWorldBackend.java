package org.valkyrienskies.mod.common.physics.physx;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import gnu.trove.map.TObjectIntMap;
import gnu.trove.map.hash.TObjectIntHashMap;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.BlockSection;
import org.valkyrienskies.mod.common.physics.PhysicsCollideWith;
import org.valkyrienskies.mod.common.physics.physx.collision.AbstractPhysXCollisionObject;
import org.valkyrienskies.mod.common.physics.physx.collision.PhysXBlockSectionCollider;
import org.valkyrienskies.mod.common.physics.physx.collision.PhysXEntityBody;
import org.valkyrienskies.mod.common.physics.physx.collision.PhysXShipBody;
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
public class PhysXWorldBackend {
    @NotNull
    private final PhysXRuntime runtime;
    @NotNull
    public final PxPhysics physics;
    @NotNull
    public final PxScene scene;
    //common physX materials for each collision object
    @NotNull
    private final EnumMap<PhysXMaterials, PxMaterial> materials = new EnumMap<>(PhysXMaterials.class);
    //list of collision objects in the entire world
    private final Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> collisionObjects = new HashMap<>();
    //separate list of liquid collision objects to deal with ship buoyancy
    private final List<AbstractPhysXCollisionObject> liquidCollisionObjects = new ArrayList<>();
    //helper to track last sync generation each collision object was seen in, so stale collision objects can be released
    private final TObjectIntMap<AbstractPhysXCollisionObject.Identifier> collisionObjectSyncGenerations = new TObjectIntHashMap<>();
    //current sync generation
    private int syncGeneration;
    private boolean closed;

    public PhysXWorldBackend() {
        this.runtime = PhysXRuntime.acquire();
        this.physics = this.runtime.physics;

        //init gravity
        PxVec3 gravityVec = new PxVec3(
            (float) VSConfig.gravityVecX,
            VSConfig.doGravity ? (float) VSConfig.gravityVecY : 0f,
            (float) VSConfig.gravityVecZ
        );

        //init scene
        PxSceneDesc sceneDesc = new PxSceneDesc(this.runtime.tolerances);
        sceneDesc.setGravity(gravityVec);
        sceneDesc.setCpuDispatcher(this.runtime.cpuDispatcher);
        sceneDesc.setFilterShader(PxTopLevelFunctions.DefaultFilterShader());
        this.scene = this.physics.createScene(sceneDesc);
        this.scene.setFlag(PxSceneFlagEnum.eENABLE_CCD, true);
        this.scene.setFlag(PxSceneFlagEnum.eENABLE_STABILIZATION, true);

        //init materials
        for (PhysXMaterials material : PhysXMaterials.values()) {
            this.materials.put(material, material.create(this.physics));
        }

        //destroy temp variables
        gravityVec.destroy();
        sceneDesc.destroy();
    }

    /**
     * This is for updating the backend (and all physics stuff) from VSWorldPhysicsLoop.
     * */
    public synchronized void update(World hostWorld, Collection<PhysicsObject> shipsWithPhysics, double timeStep) {
        if (this.closed) return;

        this.syncCollisionObjects(shipsWithPhysics);
        this.updateCollisionObjectsBeforeSimulation(hostWorld, shipsWithPhysics, timeStep);

        if (this.scene.simulate((float) timeStep)) this.scene.fetchResults(true);

        this.updateCollisionObjectsAfterSimulation(hostWorld, shipsWithPhysics, timeStep);
    }

    /**
     * For updating list of collision objects from the world.
     * */
    private void syncCollisionObjects(Collection<PhysicsObject> shipsWithPhysics) {
        this.advanceSyncGeneration();

        //-----loop over all provided ships with physics to create collision objects-----
        for (PhysicsObject ship : shipsWithPhysics) {
            //---ship objects---
            PhysXShipBody.Identifier shipIdentifier = new PhysXShipBody.Identifier(ship);
            this.markCollisionObjectSynced(shipIdentifier);
            AbstractPhysXCollisionObject shipCollisionObject = this.collisionObjects.get(shipIdentifier);
            //add if no ship body
            if (shipCollisionObject == null) {
                PhysXShipBody shipBody = new PhysXShipBody(this.physics, this.scene, this.getMaterial(PhysXMaterials.SHIP), ship);
                this.addCollisionObject(shipBody);
            }
            //update ship reference if there is
            else ((PhysXShipBody) shipCollisionObject).updateShipReference(ship);

            //---defining block and entity stuff---
            PhysicsCollideWith collideWith = ship.getPhysicsCollideWith();
            List<Entity> entities;
            List<BlockSection> blockSections;
            synchronized (collideWith) {
                entities = new ArrayList<>(collideWith.getEntities());
                blockSections = new ArrayList<>(collideWith.getBlockSections());
            }

            //---block section objects---
            for (BlockSection blockSection : blockSections) {
                PhysXBlockSectionCollider.Identifier sectionIdentifier = new PhysXBlockSectionCollider.Identifier(
                        blockSection.world(),
                        blockSection.sectionX(),
                        blockSection.sectionY(),
                        blockSection.sectionZ(),
                        blockSection.contentsHash()
                );
                this.markCollisionObjectSynced(sectionIdentifier);
                if (this.collisionObjects.get(sectionIdentifier) == null) {
                    PhysXBlockSectionCollider sectionCollider = new PhysXBlockSectionCollider(
                            this.physics,
                            this.scene,
                            blockSection.world(),
                            sectionIdentifier,
                            this.getMaterial(PhysXMaterials.WORLD),
                            this.getMaterial(PhysXMaterials.LIQUID),
                            blockSection.blocks()
                    );
                    this.addCollisionObject(sectionCollider);
                }
            }

            //---entity objects---
            for (Entity entity : entities) {
                PhysXEntityBody.Identifier entityIdentifier = new PhysXEntityBody.Identifier(entity);
                this.markCollisionObjectSynced(entityIdentifier);
                if (this.collisionObjects.get(entityIdentifier) == null) {
                    PhysXEntityBody entityBody = new PhysXEntityBody(this.physics, this.scene, this.getMaterial(PhysXMaterials.ENTITY), entity);
                    this.addCollisionObject(entityBody);
                }
            }
        }

        //-----remove collision objects we do not care about anymore-----
        Iterator<Map.Entry<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject>> iterator = this.collisionObjects.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> entry = iterator.next();
            AbstractPhysXCollisionObject.Identifier identifier = entry.getKey();
            if (this.collisionObjectSyncGenerations.get(identifier) == this.syncGeneration) continue;

            AbstractPhysXCollisionObject collisionObject = entry.getValue();
            collisionObject.release();
            iterator.remove();
            this.collisionObjectSyncGenerations.remove(identifier);
        }

        //-----rebuild list of liquid collision objects-----
        this.liquidCollisionObjects.clear();
        for (AbstractPhysXCollisionObject collisionObject : this.collisionObjects.values()) {
            if (collisionObject.hasLiquidBlocks()) this.liquidCollisionObjects.add(collisionObject);
        }
    }

    private void addCollisionObject(AbstractPhysXCollisionObject collisionObject) {
        this.collisionObjects.put(collisionObject.getIdentifier(), collisionObject);
        this.markCollisionObjectSynced(collisionObject.getIdentifier());
    }

    private void markCollisionObjectSynced(AbstractPhysXCollisionObject.Identifier identifier) {
        this.collisionObjectSyncGenerations.put(identifier, this.syncGeneration);
    }

    @NotNull
    private PxMaterial getMaterial(@NotNull PhysXMaterials material) {
        PxMaterial pxMaterial = this.materials.get(material);
        if (pxMaterial == null) {
            throw new IllegalStateException("Missing PhysX material " + material);
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
    private void updateCollisionObjectsBeforeSimulation(World hostWorld, Collection<PhysicsObject> shipsWithPhysics, double timeStep) {
        for (AbstractPhysXCollisionObject collisionObject : new ArrayList<>(this.collisionObjects.values())) {
            try {
                collisionObject.updateBeforeSimulation(hostWorld, shipsWithPhysics, this.collisionObjects, this.liquidCollisionObjects, timeStep);
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
        this.liquidCollisionObjects.clear();
        this.collisionObjectSyncGenerations.clear();

        for (PxMaterial material : this.materials.values()) material.release();
        this.materials.clear();
        this.scene.release();
        this.runtime.release();
    }
}
