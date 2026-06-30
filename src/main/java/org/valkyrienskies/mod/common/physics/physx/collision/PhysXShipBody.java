package org.valkyrienskies.mod.common.physics.physx.collision;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;
import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.block.IBlockBuoyancyProvider;
import org.valkyrienskies.mod.common.block.IBlockForceProvider;
import org.valkyrienskies.mod.common.block.IBlockTorqueProvider;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.physx.IPhysicsBlockController;
import org.valkyrienskies.mod.common.physics.physx.PhysXActorUtil;
import org.valkyrienskies.mod.common.physics.physx.PhysXCollisionFilters;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.ShipPilot;
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

import java.util.*;

/**
 * Information involving the ships collisions
 * todo: make interface and move most of the methods used here to there for when we get multiple physics engines
 * */
public class PhysXShipBody extends AbstractPhysXCollisionObject {
    private static final int MAX_SHIP_SHAPES = 8192;
    private static final double WATER_DENSITY = 1000D;
    private static final double WATER_VERTICAL_DAMPING = 1800D;
    private static final double WATER_HORIZONTAL_DAMPING = 450D;
    private static final double BLOCK_HALF_EXTENT = 0.5D;

    @NotNull
    private PhysicsObject ship;
    @NotNull
    private final Identifier identifier;
    @NotNull
    private final PxRigidDynamic actor;
    private final List<PxShape> shapes;
    @NotNull
    private final PxMaterial material;
    private int lastBlockCount;
    private boolean firstSync;
    private boolean centerOfMassPoseDirty;

    public PhysXShipBody(@NotNull PxPhysics physics, @NotNull PxScene scene, PhysicsObject ship) {
        super(physics, scene);
        this.ship = ship;
        this.identifier = new Identifier(ship);
        this.shapes = new ArrayList<>();
        this.material = physics.createMaterial(0.55f, 0.55f, 0.05f);
        this.lastBlockCount = -1;
        this.firstSync = true;
        this.centerOfMassPoseDirty = false;

        PxTransform transform = PhysXActorUtil.toPxTransform(ship.getShipTransformationManager().getCurrentPhysicsTransform());
        this.actor = this.physics.createRigidDynamic(transform);
        transform.destroy();
        this.actor.setRigidBodyFlag(PxRigidBodyFlagEnum.eENABLE_CCD, true);
        this.actor.setSolverIterationCounts(8, 2);
        this.actor.setMaxLinearVelocity((float) VSConfig.shipMaxSpeed);
        this.actor.setMaxAngularVelocity((float) VSConfig.shipMaxAngularSpeed);
        this.scene.addActor(this.actor);
    }

    private void syncBeforeSimulation() {
        PhysicsCalculations calculations = this.ship.getPhysicsCalculations();
        if (this.firstSync
                || this.ship.getPhysicsData().consumeCollisionShapeDirty()
                || this.lastBlockCount != this.ship.getBlockPositions().size()
        ) {
            this.rebuildCollisionShapes(this.ship);
        }

        boolean disableGravity = !VSConfig.doGravity || this.ship.isShipAligningToGrid() || calculations.actAsArchimedes;
        this.actor.setActorFlag(PxActorFlagEnum.eDISABLE_GRAVITY, disableGravity);
        this.actor.setMass((float) Math.max(this.ship.getInertiaData().getGameTickMass(), 0.0001f));
        this.setMassInertia(calculations.getPhysMOITensor());
        this.actor.setMaxLinearVelocity((float) VSConfig.shipMaxSpeed);
        this.actor.setMaxAngularVelocity((float) VSConfig.shipMaxAngularSpeed);

        boolean forceGameTransform = calculations.setForceToUseGameTransform(false);
        if (this.firstSync || forceGameTransform) {
            this.forcePose(this.ship.getShipData().getShipTransform());
            calculations.getLinearVelocity().zero();
            calculations.getAngularVelocity().zero();
        }
        else if (this.centerOfMassPoseDirty) {
            this.forcePose(this.ship.getShipTransformationManager().getCurrentPhysicsTransform());
        }
        this.centerOfMassPoseDirty = false;

        PxVec3 linearVelocity = PhysXActorUtil.toPxVec(calculations.getLinearVelocity());
        PxVec3 angularVelocity = PhysXActorUtil.toPxVec(calculations.getAngularVelocity());
        this.actor.setLinearVelocity(linearVelocity, true);
        this.actor.setAngularVelocity(angularVelocity, true);
        linearVelocity.destroy();
        angularVelocity.destroy();

        this.drainImpulseFrom(calculations);
        this.firstSync = false;
    }

    private void forcePose(ShipTransform transform) {
        PxTransform pxTransform = PhysXActorUtil.toPxTransform(transform);
        this.actor.setGlobalPose(pxTransform, true);
        pxTransform.destroy();
    }

    private void drainImpulseFrom(PhysicsCalculations calculations) {
        this.addImpulse(new Vector3d(calculations.getForce()), new Vector3d(calculations.getTorque()));
        calculations.getForce().zero();
        calculations.getTorque().zero();
    }

    private void addImpulse(Vector3dc impulse, Vector3dc torqueImpulse) {
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

    public void updateShipReference(@NotNull PhysicsObject ship) {
        this.ship = ship;
    }

    @Override
    @NotNull
    public Identifier getIdentifier() {
        return this.identifier;
    }

    @Override
    public void updateBeforeSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> collisionObjects,
            double timeStep
    ) {
        this.prepareForSimulation(timeStep);
        this.applyLiquidForces(hostWorld, collisionObjects);
        this.syncBeforeSimulation();
    }

    @Override
    public void updateAfterSimulation(
            @NotNull World hostWorld,
            @NotNull Collection<PhysicsObject> shipsWithPhysics,
            @NotNull Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> collisionObjects,
            double timeStep
    ) {
        PhysicsCalculations calculations = this.ship.getPhysicsCalculations();
        PxTransform pose = this.actor.getGlobalPose();
        PxVec3 posePosition = pose.getP();
        physx.common.PxQuat poseRotation = pose.getQ();
        Vector3d position = PhysXActorUtil.fromPxVec(posePosition);
        Quaterniond rotation = PhysXActorUtil.fromPxQuat(poseRotation);

        position.y = Math.clamp(position.y, VSConfig.shipLowerLimit, VSConfig.shipUpperLimit);
        ShipTransform finalTransform = new ShipTransform(position.x, position.y, position.z, rotation, new Vector3d(calculations.getPhysCenterOfMass()));

        PxVec3 linearVelocity = this.actor.getLinearVelocity();
        PxVec3 angularVelocity = this.actor.getAngularVelocity();
        calculations.getLinearVelocity().set(PhysXActorUtil.fromPxVec(linearVelocity));
        calculations.getAngularVelocity().set(PhysXActorUtil.fromPxVec(angularVelocity));
        // Getter-returned PhysX JNI value wrappers have ambiguous ownership. Do not destroy them
        // during readback; an invalid free here aborts the JVM before Minecraft can write a report.
        this.finishSimulationTick(finalTransform);
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

    private void prepareForSimulation(double timeStep) {
        PhysicsCalculations calculations = this.ship.getPhysicsCalculations();
        calculations.getForce().zero();
        calculations.getTorque().zero();
        calculations.setPhysicsTimeDeltaPerPhysTick(timeStep);
        this.centerOfMassPoseDirty = false;
        this.updatePhysCenterOfMass(calculations);
        this.calculateFramedMOITensor(calculations);

        if (!this.ship.isShipAligningToGrid()) {
            this.applyAirDrag(calculations);
            if (!calculations.actAsArchimedes) this.calculateForces(calculations);
        }
        else this.calculateForcesDeconstruction(calculations, timeStep);
    }

    private void finishSimulationTick(ShipTransform finalPhysTransform) {
        PhysicsCalculations calculations = this.ship.getPhysicsCalculations();
        calculations.getPhysCenterOfMass().set(finalPhysTransform.getCenterCoord());

        //reset all velocities when physics somehow broke
        if (this.isPhysicsBroken(calculations)) {
            this.ship.getShipData().setPhysicsEnabled(false);
            calculations.getLinearVelocity().zero();
            calculations.getAngularVelocity().zero();
        }

        this.ship.getShipTransformationManager().updatePreviousPhysicsTransform();
        this.ship.getShipTransformationManager().setCurrentPhysicsTransform(finalPhysTransform);
        this.ship.getShipData().getPhysicsData().setAngularVelocity(new Vector3d(calculations.getAngularVelocity()));
        this.ship.getShipData().getPhysicsData().setLinearVelocity(new Vector3d(calculations.getLinearVelocity()));
    }

    //fallback for if something bad happened with the physics
    private boolean isPhysicsBroken(PhysicsCalculations calculations) {
        if (calculations.getAngularVelocity().lengthSquared() > 50000
                || calculations.getLinearVelocity().lengthSquared() > 50000
                || !calculations.getAngularVelocity().isFinite()
                || !calculations.getLinearVelocity().isFinite()
        ) {
            System.out.println("Ship tried moving too fast; freezing it and resetting velocities");
            return true;
        }
        return false;
    }

    private void updatePhysCenterOfMass(PhysicsCalculations calculations) {
        Vector3d currentCenterOfMass = calculations.getPhysCenterOfMass();
        Vector3dc gameTickCenterOfMass = this.ship.getInertiaData().getGameTickCenterOfMass();
        if (!currentCenterOfMass.equals(gameTickCenterOfMass)) {
            ShipTransform currentTransform = this.ship.getShipTransformationManager().getCurrentPhysicsTransform();
            Vector3d centerDifference = gameTickCenterOfMass.sub(currentCenterOfMass, new Vector3d());
            currentTransform.transformDirection(centerDifference, TransformType.SUBSPACE_TO_GLOBAL);

            Vector3d newCenterOfMass = new Vector3d(gameTickCenterOfMass);
            ShipTransform adjustedTransform = new ShipTransform(
                    currentTransform.getPosX() + centerDifference.x,
                    currentTransform.getPosY() + centerDifference.y,
                    currentTransform.getPosZ() + centerDifference.z,
                    currentTransform.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL),
                    newCenterOfMass
            );
            currentCenterOfMass.set(newCenterOfMass);
            this.ship.getShipTransformationManager().setCurrentPhysicsTransform(adjustedTransform);
            this.centerOfMassPoseDirty = true;
            this.ship.getShipData().getPhysicsData().markCollisionShapeDirty();
        }
    }

    private void calculateFramedMOITensor(PhysicsCalculations calculations) {
        Matrix3dc rotationMatrix = this.ship.getShipTransformationManager()
                .getCurrentPhysicsTransform().createRotationMatrix(TransformType.SUBSPACE_TO_GLOBAL);
        Matrix3dc inertiaBodyFrame = this.ship.getInertiaData().getGameMoITensor();
        Matrix3d rotationMatrixTranspose = rotationMatrix.transpose(new Matrix3d());
        Matrix3d finalInertia = new Matrix3d(rotationMatrix);
        finalInertia.mul(inertiaBodyFrame);
        finalInertia.mul(rotationMatrixTranspose);
        calculations.getPhysMOITensor().set(finalInertia);
        calculations.getPhysInvMOITensor().set(finalInertia).invert();
    }

    /**
     * Well
     * */
    private void calculateForces(PhysicsCalculations calculations) {
        Vector3d blockForce = new Vector3d();
        Vector3d inBodyWO = new Vector3d();
        Vector3d crossVector = new Vector3d();
        World world = this.ship.getWorld();

        //forces from physics blocks
        if (VSConfig.doPhysicsBlocks) {
            Queue<IPhysicsBlockController> nodesPriorityQueue = new PriorityQueue<>(this.ship.getPhysicsControllersInShip());
            while (!nodesPriorityQueue.isEmpty()) {
                IPhysicsBlockController controller = nodesPriorityQueue.poll();
                controller.onPhysicsTick(this.ship, calculations, calculations.getPhysicsTimeDeltaPerPhysTick());
            }

            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
            if (this.ship.getShipData().activeForcePositions != null) {
                //iterate over active force positions
                for (BlockPos activeForcePos : this.ship.getShipData().activeForcePositions) {
                    mutablePos.setPos(activeForcePos);
                    IBlockState state = this.ship.getChunkAt(mutablePos.getX() >> 4, mutablePos.getZ() >> 4).getBlockState(mutablePos);
                    Block blockAt = state.getBlock();

                    //normal force blocks
                    if (blockAt instanceof IBlockForceProvider blockForceProvider) {
                        Vector3dc forceVector = blockForceProvider.getBlockForceInWorldSpace(
                                world, mutablePos, state,
                                this.ship, calculations.getPhysicsTimeDeltaPerPhysTick()
                        );
                        if (forceVector == null) blockForce.zero();
                        else {
                            blockForce.x = forceVector.x();
                            blockForce.y = forceVector.y();
                            blockForce.z = forceVector.z();
                        }

                        Vector3dc otherPosition = blockForceProvider.getCustomBlockForcePosition(
                                world, mutablePos, state,
                                this.ship, calculations.getPhysicsTimeDeltaPerPhysTick()
                        );

                        if (otherPosition != null) inBodyWO.set(otherPosition);
                        else inBodyWO.set(mutablePos.getX() + 0.5, mutablePos.getY() + 0.5, mutablePos.getZ() + 0.5);

                        inBodyWO.sub(calculations.getPhysCenterOfMass());
                        this.ship.getShipTransformationManager()
                                .getCurrentPhysicsTransform()
                                .transformDirection(inBodyWO, TransformType.SUBSPACE_TO_GLOBAL);
                        calculations.addForceAtPoint(inBodyWO, blockForce, crossVector);
                    }
                    //torque blocks
                    if (blockAt instanceof IBlockTorqueProvider torqueProviderBlock) {
                        Vector3dc torqueVector = torqueProviderBlock.getTorqueInGlobal(calculations, mutablePos);
                        if (torqueVector != null) calculations.getTorque().add(torqueVector);
                    }
                }
            }
        }

        //forces from pilot
        final ShipPilot parentPilot = this.ship.getShipPilot();
        if (parentPilot != null) {
            final Vector3dc pilotForce = parentPilot.getBlockForceInShipSpace(this.ship, calculations.getPhysicsTimeDeltaPerPhysTick());
            final Vector3dc pilotTorque = parentPilot.getTorqueInGlobal(calculations);
            if (pilotForce != null) calculations.getForce().add(pilotForce);
            if (pilotTorque != null) calculations.getTorque().add(pilotTorque);
        }
    }

    private void calculateForcesDeconstruction(PhysicsCalculations calculations, double timeStep) {
        this.applyAirDrag(calculations);
        Quaterniondc inverseCurrentRotation = this.ship.getShipTransformationManager()
                .getCurrentPhysicsTransform().rotationQuaternion(TransformType.GLOBAL_TO_SUBSPACE);
        AxisAngle4d idealAxisAngle = new AxisAngle4d(inverseCurrentRotation);
        if (idealAxisAngle.angle < PhysicsCalculations.EPSILON) return;
        idealAxisAngle.normalize();
        double angleBetweenIdealAndActual = idealAxisAngle.angle;
        if (angleBetweenIdealAndActual > Math.PI) {
            angleBetweenIdealAndActual = 2 * Math.PI - angleBetweenIdealAndActual;
        }
        double idealAngularVelocityMultiple = angleBetweenIdealAndActual;
        Vector3d idealAngularVelocity = new Vector3d(idealAxisAngle.x, idealAxisAngle.y, idealAxisAngle.z);
        idealAngularVelocity.mul(idealAngularVelocityMultiple);
        Vector3d angularVelocityDifference = idealAngularVelocity.sub(calculations.getAngularVelocity(), new Vector3d());
        angularVelocityDifference.mul(timeStep);
        calculations.getAngularVelocity().add(angularVelocityDifference);
    }

    private void applyAirDrag(PhysicsCalculations calculations) {
        double drag = this.getDragForPhysTick(calculations);
        calculations.getLinearVelocity().mul(drag);
        calculations.getAngularVelocity().mul(drag);
    }

    private double getDragForPhysTick(PhysicsCalculations calculations) {
        return Math.pow(PhysicsCalculations.DRAG_CONSTANT, calculations.getPhysicsTimeDeltaPerPhysTick() * 20D);
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

    /**
     * This is for applying buoyancy in liquids
     * */
    private void applyLiquidForces(World hostWorld, Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> collisionObjects) {
        AxisAlignedBB shipAabb = this.ship.getPhysicsTransformAABB();
        if (shipAabb == null || !this.isTouchingLiquidActor(shipAabb, collisionObjects)) return;

        ShipTransform transform = this.ship.getShipTransformationManager().getCurrentPhysicsTransform();
        PhysicsCalculations calculations = this.ship.getPhysicsCalculations();

        Vector3d tempTorque = new Vector3d();
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

        for (BlockPos blockPos : this.ship.getBlockPositions()) {
            IBlockState state = this.getShipBlockState(this.ship, blockPos);
            double buoyancyForce = (state.getBlock() instanceof IBlockBuoyancyProvider buoyancyProvider) ?
                    buoyancyProvider.getBuoyancyForce(hostWorld, mutablePos, state, this.ship) : WATER_DENSITY * Math.abs(VSConfig.gravityVecY);

            Vector3d centerWorld = new Vector3d(
                    blockPos.getX() + 0.5D,
                    blockPos.getY() + 0.5D,
                    blockPos.getZ() + 0.5D
            );
            transform.transformPosition(centerWorld, TransformType.SUBSPACE_TO_GLOBAL);

            double waterSurfaceY = this.getWaterSurfaceY(hostWorld, mutablePos, centerWorld);
            if (!Double.isNaN(waterSurfaceY)) {
                double submergedFraction = Math.clamp(waterSurfaceY - (centerWorld.y - BLOCK_HALF_EXTENT), 0D, 1D);
                if (submergedFraction > 0D) {
                    Vector3d relativeToShipCenter = centerWorld.sub(
                            new Vector3d(transform.getPosX(), transform.getPosY(), transform.getPosZ()),
                            new Vector3d()
                    );
                    Vector3d velocityAtPoint = calculations.getVelocityAtPoint(relativeToShipCenter, new Vector3d());
                    double lift = buoyancyForce * submergedFraction;
                    Vector3d force = new Vector3d(
                            -velocityAtPoint.x * WATER_HORIZONTAL_DAMPING * submergedFraction,
                            lift - velocityAtPoint.y * WATER_VERTICAL_DAMPING * submergedFraction,
                            -velocityAtPoint.z * WATER_HORIZONTAL_DAMPING * submergedFraction
                    );
                    calculations.addForceAtPointNew(relativeToShipCenter, force, tempTorque);
                }
            }
        }
    }

    private boolean isTouchingLiquidActor(AxisAlignedBB shipAabb, Map<AbstractPhysXCollisionObject.Identifier, AbstractPhysXCollisionObject> collisionObjects) {
        for (AbstractPhysXCollisionObject collisionObject : collisionObjects.values()) {
            if (collisionObject.isLiquidBlockIntersecting(shipAabb)) return true;
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

    public static final class Identifier extends AbstractPhysXCollisionObject.Identifier {
        @NotNull
        private final UUID shipUuid;

        public Identifier(@NotNull PhysicsObject ship) {
            this.shipUuid = ship.getUuid();
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Identifier that)) return false;
            return this.shipUuid.equals(that.shipUuid);
        }

        @Override
        public int hashCode() {
            return this.shipUuid.hashCode();
        }
    }
}
