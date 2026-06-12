package org.valkyrienskies.mod.common.physics.collision;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.BlockPhysicsDetails;
import org.valkyrienskies.mod.common.physics.PhysXActorUtil;
import org.valkyrienskies.mod.common.physics.PhysXCollisionFilters;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import physx.common.PxTransform;
import physx.common.PxVec3;
import physx.physics.PxActorFlagEnum;
import physx.physics.PxForceModeEnum;
import physx.physics.PxMaterial;
import physx.physics.PxPhysics;
import physx.physics.PxRigidActor;
import physx.physics.PxRigidBodyFlagEnum;
import physx.physics.PxRigidDynamic;
import physx.physics.PxScene;
import physx.physics.PxShape;
import valkyrienwarfare.api.TransformType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Information involving the ships collisions.
 * todo: put an interface on this that will be useful for when we add option for multiple physics engines
 * */
public class PhysXShipBody extends AbstractPhysXCollisionObject {
    private static final int MAX_SHIP_SHAPES = 8192;
    private static final int MAX_BUOYANCY_BLOCKS_PER_TICK = 8192;
    private static final double WATER_DENSITY = 1000D;
    private static final double WATER_VERTICAL_DAMPING = 1800D;
    private static final double WATER_HORIZONTAL_DAMPING = 450D;
    private static final double BLOCK_HALF_EXTENT = 0.5D;

    @NotNull
    private PhysicsObject ship;
    @NotNull
    private final PxRigidDynamic actor;
    private final List<PxShape> shapes;
    @NotNull
    private final PxMaterial material;
    private int lastBlockCount;
    private boolean firstSync;

    public PhysXShipBody(@NotNull PxPhysics physics, @NotNull PxScene scene, PhysicsObject ship) {
        super(physics, scene);
        this.ship = ship;
        this.shapes = new ArrayList<>();
        this.material = physics.createMaterial(0.55f, 0.55f, 0.05f);
        this.lastBlockCount = -1;
        this.firstSync = true;

        PxTransform transform = PhysXActorUtil.toPxTransform(ship.getShipTransformationManager().getCurrentPhysicsTransform());
        this.actor = this.physics.createRigidDynamic(transform);
        transform.destroy();
        this.actor.setRigidBodyFlag(PxRigidBodyFlagEnum.eENABLE_CCD, true);
        this.actor.setSolverIterationCounts(8, 2);
        this.actor.setMaxLinearVelocity((float) VSConfig.shipMaxSpeed);
        this.actor.setMaxAngularVelocity((float) VSConfig.shipMaxAngularSpeed);
        this.scene.addActor(this.actor);
    }

    public void syncBeforeSimulation() {
        PhysicsCalculations calculations = this.ship.getPhysicsCalculations();
        if (this.firstSync
                || this.ship.getPhysicsData().consumeCollisionShapeDirty()
                || this.lastBlockCount != this.ship.getBlockPositions().size()
        ) {
            this.rebuildCollisionShapes(this.ship);
        }

        boolean disableGravity = !VSConfig.doGravity || this.ship.isShipAligningToGrid() || calculations.actAsArchimedes;
        this.actor.setActorFlag(PxActorFlagEnum.eDISABLE_GRAVITY, disableGravity);
        this.actor.setMass(Math.max((float) calculations.getMass(), 0.0001f));
        this.setMassInertia(calculations.getPhysMOITensor());
        this.actor.setMaxLinearVelocity((float) VSConfig.shipMaxSpeed);
        this.actor.setMaxAngularVelocity((float) VSConfig.shipMaxAngularSpeed);

        boolean forceGameTransform = calculations.consumeForceToUseGameTransform();
        boolean centerOfMassPoseDirty = calculations.consumeCenterOfMassPoseDirty();
        if (this.firstSync || forceGameTransform) {
            this.forcePose(this.ship.getShipData().getShipTransform());
            calculations.getLinearVelocity().zero();
            calculations.getAngularVelocity().zero();
        }
        else if (centerOfMassPoseDirty) {
            this.forcePose(calculations.createPhysTransform());
        }

        PxVec3 linearVelocity = PhysXActorUtil.toPxVec(calculations.getLinearVelocity());
        PxVec3 angularVelocity = PhysXActorUtil.toPxVec(calculations.getAngularVelocity());
        this.actor.setLinearVelocity(linearVelocity, true);
        this.actor.setAngularVelocity(angularVelocity, true);
        linearVelocity.destroy();
        angularVelocity.destroy();

        calculations.drainImpulseTo(this);
        this.firstSync = false;
    }

    public void forcePose(ShipTransform transform) {
        PxTransform pxTransform = PhysXActorUtil.toPxTransform(transform);
        this.actor.setGlobalPose(pxTransform, true);
        pxTransform.destroy();
    }

    public void addImpulse(Vector3dc impulse, Vector3dc torqueImpulse) {
        if (impulse.lengthSquared() > 0) {
            PxVec3 force = PhysXActorUtil.toPxVec(impulse);
            this.actor.addForce(force, PxForceModeEnum.eIMPULSE, true);
            force.destroy();
        }
        if (torqueImpulse.lengthSquared() > 0) {
            PxVec3 torque = PhysXActorUtil.toPxVec(torqueImpulse);
            this.actor.addTorque(torque, PxForceModeEnum.eIMPULSE, true);
            torque.destroy();
        }
    }

    @NotNull
    public PhysicsObject getShip() {
        return this.ship;
    }

    @Override
    public boolean isStillValid(@NotNull World hostWorld, @NotNull Collection<PhysicsObject> shipsWithPhysics) {
        UUID shipId = this.ship.getUuid();
        for (PhysicsObject loadedShip : shipsWithPhysics) {
            if (shipId.equals(loadedShip.getUuid())) {
                this.ship = loadedShip;
                return true;
            }
        }
        return false;
    }

    @Override
    public void updateBeforeSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull List<AbstractPhysXCollisionObject> collisionObjects,
            double timeStep
    ) {
        this.ship.getPhysicsCalculations().prePhysXTick(timeStep);
        this.applyLiquidForces(hostWorld, collisionObjects);
        this.syncBeforeSimulation();
    }

    @Override
    public void updateAfterSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull List<AbstractPhysXCollisionObject> collisionObjects,
            double timeStep
    ) {
        PhysicsCalculations calculations = this.ship.getPhysicsCalculations();
        PxTransform pose = this.actor.getGlobalPose();
        PxVec3 posePosition = pose.getP();
        physx.common.PxQuat poseRotation = pose.getQ();
        Vector3d position = PhysXActorUtil.fromPxVec(posePosition);
        Quaterniond rotation = PhysXActorUtil.fromPxQuat(poseRotation);

        position.y = Math.clamp(position.y, VSConfig.shipLowerLimit, VSConfig.shipUpperLimit);
        ShipTransform finalTransform = new ShipTransform(position.x, position.y, position.z, rotation, calculations.getPhysCenterOfMass());

        PxVec3 linearVelocity = this.actor.getLinearVelocity();
        PxVec3 angularVelocity = this.actor.getAngularVelocity();
        calculations.setLinearVelocity(PhysXActorUtil.fromPxVec(linearVelocity));
        calculations.setAngularVelocity(PhysXActorUtil.fromPxVec(angularVelocity));
        // Getter-returned PhysX JNI value wrappers have ambiguous ownership. Do not destroy them
        // during readback; an invalid free here aborts the JVM before Minecraft can write a report.
        calculations.finishPhysXTick(finalTransform);
    }

    @Override
    protected void releaseShapes() {
        for (PxShape shape : this.shapes) this.detachShape(shape);
        this.shapes.clear();
    }

    @Override
    @NotNull
    public PxMaterial getMaterial() {
        return this.material;
    }

    @Override
    @NotNull
    protected PxRigidActor getActor() {
        return this.actor;
    }

    private void rebuildCollisionShapes(PhysicsObject ship) {
        for (PxShape shape : this.shapes) this.detachShape(shape);
        this.shapes.clear();

        Vector3dc centerOfMass = ship.getPhysicsCalculations().getPhysCenterOfMass();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        boolean truncated = false;

        for (BlockPos blockPos : ship.getBlockPositions()) {
            if (this.shapes.size() >= MAX_SHIP_SHAPES) {
                truncated = true;
                break;
            }
            mutablePos.setPos(blockPos);
            IBlockState state = this.getShipBlockState(ship, mutablePos);
            if (!this.isShipCollisionState(state) || this.isInteriorFullCube(ship, mutablePos, state)) {
                continue;
            }
            List<AxisAlignedBB> boxes = PhysXBlockCollider.getCollisionBoxes(ship.getWorld(), mutablePos, state, false);
            for (AxisAlignedBB box : boxes) {
                if (this.shapes.size() >= MAX_SHIP_SHAPES) {
                    truncated = true;
                    break;
                }
                this.attachShipShape(box, centerOfMass);
            }
        }

        if (truncated) {
            System.err.println("PhysX ship " + ship.getName() + " exceeded " + MAX_SHIP_SHAPES
                + " collision shapes; extra block shapes were skipped in this preliminary backend.");
        }
        this.lastBlockCount = ship.getBlockPositions().size();
    }

    private void attachShipShape(AxisAlignedBB box, Vector3dc centerOfMass) {
        PxShape shape = this.createBoxShape(box);
        PhysXCollisionFilters.CollisionGroup.SHIP.setFilter(shape);

        double localX = (box.minX + box.maxX) * 0.5D - centerOfMass.x();
        double localY = (box.minY + box.maxY) * 0.5D - centerOfMass.y();
        double localZ = (box.minZ + box.maxZ) * 0.5D - centerOfMass.z();
        PxTransform localPose = PhysXActorUtil.toPxTransform(localX, localY, localZ, new Quaterniond());
        shape.setLocalPose(localPose);
        localPose.destroy();

        if (this.attachShape(shape)) this.shapes.add(shape);
    }

    private void setMassInertia(Matrix3dc inertiaTensor) {
        float ix = (float) Math.max(inertiaTensor.m00(), 0.0001D);
        float iy = (float) Math.max(inertiaTensor.m11(), 0.0001D);
        float iz = (float) Math.max(inertiaTensor.m22(), 0.0001D);
        PxVec3 inertia = new PxVec3(ix, iy, iz);
        this.actor.setMassSpaceInertiaTensor(inertia);
        inertia.destroy();
    }

    private boolean isShipCollisionState(IBlockState state) {
        if (state == null || state.getBlock() == Blocks.AIR || state.getMaterial() == Material.AIR) {
            return false;
        }
        return !PhysXBlockCollider.isLiquid(state) && state.getMaterial().blocksMovement();
    }

    private boolean isInteriorFullCube(PhysicsObject ship, BlockPos pos, IBlockState state) {
        if (!state.isFullCube()) return false;
        return ship.getBlockPositions().contains(pos.getX() + 1, pos.getY(), pos.getZ())
            && ship.getBlockPositions().contains(pos.getX() - 1, pos.getY(), pos.getZ())
            && ship.getBlockPositions().contains(pos.getX(), pos.getY() + 1, pos.getZ())
            && ship.getBlockPositions().contains(pos.getX(), pos.getY() - 1, pos.getZ())
            && ship.getBlockPositions().contains(pos.getX(), pos.getY(), pos.getZ() + 1)
            && ship.getBlockPositions().contains(pos.getX(), pos.getY(), pos.getZ() - 1);
    }

    private IBlockState getShipBlockState(PhysicsObject ship, BlockPos pos) {
        Chunk chunk = ship.getChunkAt(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return Blocks.AIR.getDefaultState();
        return chunk.getBlockState(pos);
    }

    private void applyLiquidForces(World hostWorld, List<AbstractPhysXCollisionObject> collisionObjects) {
        AxisAlignedBB shipAabb = this.ship.getPhysicsTransformAABB();
        if (shipAabb == null || !this.isTouchingLiquidActor(shipAabb, collisionObjects)) return;

        ShipTransform transform = this.ship.getShipTransformationManager().getCurrentPhysicsTransform();
        PhysicsCalculations calculations = this.ship.getPhysicsCalculations();
        double gravityMagnitude = Math.abs(VSConfig.gravityVecY);
        if (gravityMagnitude <= 0D) return;

        int processed = 0;
        Vector3d tempTorque = new Vector3d();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (BlockPos blockPos : this.ship.getBlockPositions()) {
            if (processed++ >= MAX_BUOYANCY_BLOCKS_PER_TICK) break;

            IBlockState state = this.getShipBlockState(this.ship, blockPos);
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

    private boolean isTouchingLiquidActor(AxisAlignedBB shipAabb, List<AbstractPhysXCollisionObject> collisionObjects) {
        for (AbstractPhysXCollisionObject collisionObject : collisionObjects) {
            if (collisionObject.isLiquidBlockIntersecting(shipAabb)) {
                return true;
            }
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

}
