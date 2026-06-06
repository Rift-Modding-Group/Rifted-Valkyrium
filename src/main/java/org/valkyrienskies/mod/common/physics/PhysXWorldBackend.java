package org.valkyrienskies.mod.common.physics;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.collision.PhysXBlockCollider;
import org.valkyrienskies.mod.common.physics.collision.PhysXEntityBody;
import org.valkyrienskies.mod.common.physics.collision.PhysXShipBody;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import physx.PxTopLevelFunctions;
import physx.common.PxVec3;
import physx.physics.PxPhysics;
import physx.physics.PxScene;
import physx.physics.PxSceneDesc;
import physx.physics.PxSceneFlagEnum;
import valkyrienwarfare.api.TransformType;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * One PhysX scene for a loaded Minecraft dimension.
 * VS ship blocks remain in their shipyard chunks. This backend creates projected
 * PhysX actors for those ships and for nearby blocks, liquid, and entities.
 */
public class PhysXWorldBackend {
    private static final int MAX_WORLD_BLOCK_ACTORS_PER_TICK = 12000;
    private static final int MAX_ENTITY_ACTORS_PER_TICK = 512;
    private static final int MAX_BUOYANCY_BLOCKS_PER_TICK = 8192;
    private static final double WORLD_ACTOR_SCAN_GROW = 3D;
    private static final double ENTITY_ACTOR_SCAN_GROW = 2D;
    private static final double WATER_DENSITY = 1000D;
    private static final double WATER_VERTICAL_DAMPING = 1800D;
    private static final double WATER_HORIZONTAL_DAMPING = 450D;
    private static final double BLOCK_HALF_EXTENT = 0.5D;

    @NotNull
    private final PhysXRuntime runtime;
    @NotNull
    public final PxPhysics physics;
    @NotNull
    public final PxScene scene;

    private final Map<UUID, PhysXShipBody> shipBodies = new HashMap<>();
    private final Map<BlockPos, PhysXBlockCollider> worldBlockActors = new HashMap<>();
    private final Map<Integer, PhysXEntityBody> entityActors = new HashMap<>();
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

        this.syncShipActors(shipsWithPhysics);
        this.syncWorldBlockActors(hostWorld, shipsWithPhysics);
        this.syncEntityActors(hostWorld, shipsWithPhysics);

        for (PhysicsObject ship : shipsWithPhysics) {
            try {
                ship.getPhysicsCalculations().prePhysXTick(timeStep);
                PhysXShipBody body = this.shipBodies.computeIfAbsent(ship.getUuid(), ignored -> new PhysXShipBody(this.physics, this.scene, ship));
                this.applyLiquidForces(hostWorld, ship);
                body.syncBeforeSimulation(ship);
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }

        if (this.scene.simulate((float) timeStep)) {
            this.scene.fetchResults(true);
        }

        for (PhysicsObject ship : shipsWithPhysics) {
            PhysXShipBody body = this.shipBodies.get(ship.getUuid());
            if (body != null) {
                try {
                    body.readBackTo(ship);
                }
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }

    //-----ship managing-----
    private void syncShipActors(Collection<PhysicsObject> shipsWithPhysics) {
        Set<UUID> loadedShipIds = new HashSet<>();
        for (PhysicsObject ship : shipsWithPhysics) {
            loadedShipIds.add(ship.getUuid());
        }
        Iterator<Map.Entry<UUID, PhysXShipBody>> iterator = this.shipBodies.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, PhysXShipBody> entry = iterator.next();
            if (!loadedShipIds.contains(entry.getKey())) {
                entry.getValue().release();
                iterator.remove();
            }
        }
    }

    //-----block pos managing-----
    private void syncWorldBlockActors(World hostWorld, Collection<PhysicsObject> shipsWithPhysics) {
        Set<BlockPos> touched = new HashSet<>();
        int scanned = 0;
        for (PhysicsObject ship : shipsWithPhysics) {
            AxisAlignedBB shipAabb = ship.getPhysicsTransformAABB();
            if (shipAabb == null) continue;
            AxisAlignedBB scan = shipAabb.grow(WORLD_ACTOR_SCAN_GROW);
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
                        if (scanned++ > MAX_WORLD_BLOCK_ACTORS_PER_TICK) {
                            this.pruneUntouchedWorldActors(touched);
                            return;
                        }
                        mutablePos.setPos(x, y, z);
                        IBlockState state = hostWorld.getBlockState(mutablePos);
                        if (!PhysXBlockCollider.isCollidableWorldState(state)) {
                            continue;
                        }
                        BlockPos immutablePos = mutablePos.toImmutable();
                        touched.add(immutablePos);
                        PhysXBlockCollider existing = this.worldBlockActors.get(immutablePos);
                        if (existing != null && existing.matches(state)) continue;
                        if (existing != null) existing.release();
                        this.worldBlockActors.put(immutablePos, new PhysXBlockCollider(this.physics, this.scene, hostWorld, immutablePos, state));
                    }
                }
            }
        }
        this.pruneUntouchedWorldActors(touched);
    }

    private void pruneUntouchedWorldActors(Set<BlockPos> touched) {
        Iterator<Map.Entry<BlockPos, PhysXBlockCollider>> iterator = this.worldBlockActors.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<BlockPos, PhysXBlockCollider> entry = iterator.next();
            if (!touched.contains(entry.getKey())) {
                entry.getValue().release();
                iterator.remove();
            }
        }
    }

    //---subsection for, well, liquid interactions---
    private void applyLiquidForces(World hostWorld, PhysicsObject ship) {
        AxisAlignedBB shipAabb = ship.getPhysicsTransformAABB();
        if (shipAabb == null || !this.isTouchingLiquidActor(shipAabb)) return;

        ShipTransform transform = ship.getShipTransformationManager().getCurrentPhysicsTransform();
        PhysicsCalculations calculations = ship.getPhysicsCalculations();
        double gravityMagnitude = Math.abs(VSConfig.gravityVecY);
        if (gravityMagnitude <= 0D) return;

        int processed = 0;
        Vector3d tempTorque = new Vector3d();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (BlockPos blockPos : ship.getBlockPositions()) {
            if (processed++ >= MAX_BUOYANCY_BLOCKS_PER_TICK) break;

            IBlockState state = this.getShipBlockState(ship, blockPos);
            if (BlockPhysicsDetails.getMassFromState(state) <= 0D) continue;

            Vector3d centerWorld = new Vector3d(blockPos.getX() + 0.5D, blockPos.getY() + 0.5D, blockPos.getZ() + 0.5D);
            transform.transformPosition(centerWorld, TransformType.SUBSPACE_TO_GLOBAL);

            double waterSurfaceY = this.getWaterSurfaceY(hostWorld, mutablePos, centerWorld);
            if (Double.isNaN(waterSurfaceY)) continue;

            double submergedFraction = Math.clamp(waterSurfaceY - (centerWorld.y - BLOCK_HALF_EXTENT), 0D, 1D);
            if (submergedFraction <= 0D) continue;

            Vector3d relativeToShipCenter = centerWorld.sub(
                    new Vector3d(transform.getPosX(), transform.getPosY(), transform.getPosZ()),
                    new Vector3d()
            );
            Vector3d velocityAtPoint = calculations.getVelocityAtPoint(relativeToShipCenter, new Vector3d());
            double lift = WATER_DENSITY * submergedFraction * gravityMagnitude;
            Vector3d force = new Vector3d(
                    -velocityAtPoint.x * WATER_HORIZONTAL_DAMPING * submergedFraction,
                    lift - velocityAtPoint.y * WATER_VERTICAL_DAMPING * submergedFraction,
                    -velocityAtPoint.z * WATER_HORIZONTAL_DAMPING * submergedFraction
            );
            calculations.addForceAtPointNew(relativeToShipCenter, force, tempTorque);
        }
    }

    private boolean isTouchingLiquidActor(AxisAlignedBB shipAabb) {
        for (PhysXBlockCollider collider : this.worldBlockActors.values()) {
            if (!collider.isLiquid()) continue;
            AxisAlignedBB liquidBox = new AxisAlignedBB(collider.getPos());
            if (shipAabb.intersects(liquidBox)) return true;
        }
        return false;
    }

    private double getWaterSurfaceY(World world, BlockPos.MutableBlockPos mutablePos, Vector3d centerWorld) {
        int x = (int) Math.floor(centerWorld.x);
        int z = (int) Math.floor(centerWorld.z);
        int minY = Math.max(0, (int) Math.floor(centerWorld.y - BLOCK_HALF_EXTENT - 0.25D));
        int maxY = Math.min(world.getHeight() - 1, (int) Math.floor(centerWorld.y + BLOCK_HALF_EXTENT));
        for (int y = maxY; y >= minY; y--) {
            mutablePos.setPos(x, y, z);
            if (PhysXBlockCollider.isLiquid(world.getBlockState(mutablePos))) {
                return y + 1D;
            }
        }
        return Double.NaN;
    }

    private IBlockState getShipBlockState(PhysicsObject ship, BlockPos pos) {
        Chunk chunk = ship.getChunkAt(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return Blocks.AIR.getDefaultState();
        return chunk.getBlockState(pos);
    }

    //-----entity managing-----
    private void syncEntityActors(World hostWorld, Collection<PhysicsObject> shipsWithPhysics) {
        Set<Integer> touched = new HashSet<>();
        int synced = 0;
        for (PhysicsObject ship : shipsWithPhysics) {
            AxisAlignedBB shipAabb = ship.getPhysicsTransformAABB();
            if (shipAabb == null) continue;
            AxisAlignedBB entityScan = shipAabb.grow(ENTITY_ACTOR_SCAN_GROW);
            List<Entity> entities = hostWorld.getEntitiesWithinAABB(Entity.class, entityScan, entity -> this.shouldCreateEntityActor(entity, hostWorld));
            for (Entity entity : entities) {
                if (synced++ > MAX_ENTITY_ACTORS_PER_TICK) {
                    this.pruneUntouchedEntityActors(touched);
                    return;
                }
                touched.add(entity.getEntityId());
                PhysXEntityBody body = this.entityActors.get(entity.getEntityId());
                if (body == null) {
                    this.entityActors.put(entity.getEntityId(), new PhysXEntityBody(this.physics, this.scene, entity));
                }
                else body.sync(entity);
            }
        }
        this.pruneUntouchedEntityActors(touched);
    }

    private void pruneUntouchedEntityActors(Set<Integer> touched) {
        Iterator<Map.Entry<Integer, PhysXEntityBody>> iterator = this.entityActors.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<Integer, PhysXEntityBody> entry = iterator.next();
            if (!touched.contains(entry.getKey())) {
                entry.getValue().release();
                iterator.remove();
            }
        }
    }

    private boolean shouldCreateEntityActor(Entity entity, World hostWorld) {
        return entity != null
            && entity.isEntityAlive()
            && !entity.noClip
            && entity.world == hostWorld
            && !(entity instanceof EntityPlayer)
            && !(entity instanceof EntityItem)
            && !(entity instanceof EntityFireball);
    }

    /**
     * To free up some memory, physics related stuff must be stopped
     * when the dimension using this backend is no longer loaded.
     * */
    public synchronized void close() {
        if (this.closed) return;
        this.closed = true;

        for (PhysXEntityBody body : this.entityActors.values()) {
            body.release();
        }
        this.entityActors.clear();
        for (PhysXBlockCollider collider : this.worldBlockActors.values()) {
            collider.release();
        }
        this.worldBlockActors.clear();
        for (PhysXShipBody body : this.shipBodies.values()) {
            body.release();
        }
        this.shipBodies.clear();

        this.scene.release();
        this.runtime.release();
    }
}
