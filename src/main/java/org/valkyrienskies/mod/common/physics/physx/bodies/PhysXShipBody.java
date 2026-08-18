package org.valkyrienskies.mod.common.physics.physx.bodies;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jspecify.annotations.NonNull;
import org.valkyrienskies.mod.common.block.IBlockBuoyancyProvider;
import org.valkyrienskies.mod.common.block.IBlockForceProvider;
import org.valkyrienskies.mod.common.block.IBlockTorqueProvider;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.GreedyBlockMerger;
import org.valkyrienskies.mod.common.physics.PhysicsUtils;
import org.valkyrienskies.mod.common.physics.physx.PhysXActorUtil;
import org.valkyrienskies.mod.common.physics.physx.PhysXActor;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import physx.common.PxQuat;
import physx.common.PxTransform;
import physx.common.PxVec3;
import physx.extensions.PxRigidBodyExt;
import physx.physics.*;
import valkyrienwarfare.api.TransformType;

import java.util.*;

/**
 * Information involving the ships collisions
 * todo: make interface and move most of the methods used here to there for when we get multiple physics engines
 * */
public class PhysXShipBody extends AbstractPhysXCollisionObject<PhysXShipBody.Identifier> {
    private static final int MAX_SHIP_SHAPES = 8192;
    private static final double WATER_DENSITY = 1000D;
    private static final double WATER_VERTICAL_DAMPING = 1800D;
    private static final double WATER_HORIZONTAL_DAMPING = 450D;
    private static final double BLOCK_HALF_EXTENT = 0.5D;
    private static final double MASS_PROPERTY_EPSILON = 1.0E-6D;

    @NotNull
    private PhysicsObject ship;
    private final Map<BlockPos, List<PxShape>> shapesByBlock;
    private final Map<PxShape, List<BlockPos>> blocksByShape;
    @NotNull
    private final PxMaterial material;
    private int lastBlockCount;
    private boolean firstSync;
    private boolean centerOfMassPoseDirty;
    private boolean massPropertiesDirty;
    private boolean massPropertiesInitialized;
    private double lastAppliedMass;

    public PhysXShipBody(
            @NotNull PhysXShipBody.Identifier identifier, @NotNull PxPhysics physics, @NotNull PxScene scene,
            @NotNull PxMaterial material, @NonNull PhysicsObject ship
    ) {
        super(identifier, physics, scene, () -> {
            PxTransform transform = createActorPose(ship, ship.getShipTransformationManager().getCurrentPhysicsTransform());
            PxRigidDynamic toReturn = physics.createRigidDynamic(transform);
            transform.destroy();
            setCenterOfMassPose(toReturn, ship, ship.getPhysicsCalculations().getPhysCenterOfMass());
            toReturn.setRigidBodyFlag(PxRigidBodyFlagEnum.eENABLE_CCD, true);
            toReturn.setSolverIterationCounts(8, 2);
            toReturn.setMaxLinearVelocity((float) VSConfig.shipMaxSpeed);
            toReturn.setMaxAngularVelocity((float) VSConfig.shipMaxAngularSpeed);
            return toReturn;
        });
        this.ship = ship;
        this.shapesByBlock = new HashMap<>();
        this.blocksByShape = new IdentityHashMap<>();
        this.material = material;
        this.lastBlockCount = -1;
        this.firstSync = true;
        this.centerOfMassPoseDirty = false;
        this.massPropertiesDirty = true;
        this.massPropertiesInitialized = false;
    }

    private void forcePose(ShipTransform transform) {
        PxTransform pxTransform = createActorPose(this.ship, transform);
        this.actor.setGlobalPose(pxTransform, true);
        pxTransform.destroy();
    }

    @NotNull
    public PhysicsObject getShip() {
        return this.ship;
    }

    public void updateShipReference(@NotNull PhysicsObject ship) {
        this.ship = ship;
    }

    /**
     * Copies the current PhysX actor pose. This must only be called by the physics
     * thread, before or after scene simulation.
     */
    public void copyActorPose(@NotNull Vector3d positionDestination, @NotNull Quaterniond rotationDestination) {
        PxTransform pose = this.actor.getGlobalPose();
        positionDestination.set(PhysXActorUtil.fromPxVec(pose.getP()));
        rotationDestination.set(PhysXActorUtil.fromPxQuat(pose.getQ()));
    }

    public void updateBeforeSimulation(@NotNull World hostWorld, @NotNull List<PhysXBlockSectionBody> blockSectionsWithLiquids, double timeStep) {
        PhysicsCalculations calculations = this.ship.getPhysicsCalculations();
        PxRigidDynamic shipActor = (PxRigidDynamic) this.actor;

        //reset force state and record this simulation step length.
        calculations.resetForceAndTorque();
        calculations.setPhysicsTimeDeltaPerPhysTick(timeStep);
        this.centerOfMassPoseDirty = false;

        //move the physics transform when the game-tick center of mass changes.
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
            this.massPropertiesDirty = true;
        }

        //rotate the ship inertia tensor into world space for this physics step.
        Matrix3dc rotationMatrix = this.ship.getShipTransformationManager()
                .getCurrentPhysicsTransform().createRotationMatrix(TransformType.SUBSPACE_TO_GLOBAL);
        Matrix3dc inertiaBodyFrame = this.ship.getInertiaData().getGameMoITensor();
        Matrix3d rotationMatrixTranspose = rotationMatrix.transpose(new Matrix3d());
        Matrix3d finalInertia = new Matrix3d(rotationMatrix);
        finalInertia.mul(inertiaBodyFrame);
        finalInertia.mul(rotationMatrixTranspose);
        calculations.getPhysMOITensor().set(finalInertia);
        calculations.getPhysInvMOITensor().set(finalInertia).invert();

        //apply normal block forces or grid-alignment forces.
        if (!this.ship.isShipAligningToGrid()) {
            this.applyAirDrag(calculations);
            if (!calculations.actAsArchimedes) this.calculateForces(calculations);
        }
        else this.calculateForcesDeconstruction(calculations, timeStep);

        //apply buoyancy and water drag only when touching liquid actors
        AxisAlignedBB shipAabb = this.ship.getPhysicsTransformAABB();
        if (shipAabb != null && this.isTouchingLiquidActor(shipAabb, blockSectionsWithLiquids)) {
            //sample each ship block against nearby water and apply submerged force.
            ShipTransform transform = this.ship.getShipTransformationManager().getCurrentPhysicsTransform();

            Vector3d tempTorque = new Vector3d();
            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();

            //iterate over each block position
            for (BlockPos blockPos : this.ship.getBlockPositions()) {
                mutablePos.setPos(blockPos);
                IBlockState state = this.getShipBlockState(this.ship, mutablePos);
                double buoyancyForce = (state.getBlock() instanceof IBlockBuoyancyProvider buoyancyProvider) ?
                        buoyancyProvider.getBuoyancyForce(hostWorld, mutablePos, state, this.ship) : WATER_DENSITY * Math.abs(VSConfig.gravityVecY);

                Vector3d centerWorld = new Vector3d(
                        blockPos.getX() + 0.5D,
                        blockPos.getY() + 0.5D,
                        blockPos.getZ() + 0.5D
                );
                transform.transformPosition(centerWorld, TransformType.SUBSPACE_TO_GLOBAL);

                double submergedFraction = this.getSubmergedFraction(hostWorld, mutablePos, centerWorld);
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
                    calculations.addForceAtPoint(relativeToShipCenter, force, tempTorque);
                }
            }
        }

        //rebuild or patch collision shapes when ship block collision data changed.
        boolean collisionShapeDirty = this.ship.getPhysicsData().consumeCollisionShapeDirty();
        Set<BlockPos> dirtyCollisionShapePositions = this.ship.getPhysicsData().consumeDirtyCollisionShapePositions();
        boolean blockCountChanged = this.lastBlockCount != this.ship.getBlockPositions().size();
        if (this.firstSync || collisionShapeDirty || (blockCountChanged && dirtyCollisionShapePositions.isEmpty())) {
            this.rebuildCollisionShapes();
            this.massPropertiesDirty = true;
        }
        else if (!dirtyCollisionShapePositions.isEmpty()) {
            this.updateCollisionShapes(dirtyCollisionShapePositions);
            this.massPropertiesDirty = true;
        }

        //push gravity, mass, and velocity-limit settings into the PhysX actor.
        boolean disableGravity = !VSConfig.doGravity || this.ship.isShipAligningToGrid() || calculations.actAsArchimedes;
        this.actor.setActorFlag(PxActorFlagEnum.eDISABLE_GRAVITY, disableGravity);
        this.updateMassPropertiesIfNeeded(calculations);
        shipActor.setMaxLinearVelocity((float) VSConfig.shipMaxSpeed);
        shipActor.setMaxAngularVelocity((float) VSConfig.shipMaxAngularSpeed);

        //force actor pose when requested or when COM movement shifted the actor frame.
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

        //copy game-side linear and angular velocity into PhysX.
        PxVec3 linearVelocity = PhysXActorUtil.toPxVec(calculations.getLinearVelocity());
        PxVec3 angularVelocity = PhysXActorUtil.toPxVec(calculations.getAngularVelocity());
        shipActor.setLinearVelocity(linearVelocity, true);
        shipActor.setAngularVelocity(angularVelocity, true);
        linearVelocity.destroy();
        angularVelocity.destroy();

        //apply deferred impulses to physx body and finish first-sync bookkeeping.
        Vector3d shipForce = new Vector3d();
        Vector3d shipTorque = new Vector3d();
        calculations.drainForceAndTorque(shipForce, shipTorque);
        if (shipForce.lengthSquared() > 0) {
            PxVec3 physXForce = PhysXActorUtil.toPxVec(shipForce);
            shipActor.addForce(physXForce, PxForceModeEnum.eIMPULSE, true);
            physXForce.destroy();
        }
        if (shipTorque.lengthSquared() > 0) {
            PxVec3 physXTorque = PhysXActorUtil.toPxVec(shipTorque);
            shipActor.addTorque(physXTorque, PxForceModeEnum.eIMPULSE, true);
            physXTorque.destroy();
        }
        this.firstSync = false;
    }

    public void updateAfterSimulation() {
        PhysicsCalculations calculations = this.ship.getPhysicsCalculations();
        PxRigidDynamic shipActor = (PxRigidDynamic) this.actor;
        PxTransform pose = this.actor.getGlobalPose();
        PxVec3 posePosition = pose.getP();
        PxQuat poseRotation = pose.getQ();
        Vector3d referencePosition = PhysXActorUtil.fromPxVec(posePosition);
        Quaterniond rotation = PhysXActorUtil.fromPxQuat(poseRotation);

        Vector3d centerPosition = getReferenceToCenterOfMass(this.ship, calculations.getPhysCenterOfMass());
        rotation.transform(centerPosition);
        referencePosition.add(centerPosition, centerPosition);
        centerPosition.y = Math.clamp(centerPosition.y, VSConfig.shipLowerLimit, VSConfig.shipUpperLimit);

        PxVec3 linearVelocity = shipActor.getLinearVelocity();
        PxVec3 angularVelocity = shipActor.getAngularVelocity();
        Vector3d finalLinearVelocity = PhysXActorUtil.fromPxVec(linearVelocity);
        Vector3d finalAngularVelocity = PhysXActorUtil.fromPxVec(angularVelocity);

        ShipTransform finalTransform = new ShipTransform(
                centerPosition.x,
                centerPosition.y,
                centerPosition.z,
                rotation,
                new Vector3d(calculations.getPhysCenterOfMass())
        );

        calculations.getLinearVelocity().set(finalLinearVelocity);
        calculations.getAngularVelocity().set(finalAngularVelocity);

        //---finish simulation tick---
        calculations.getPhysCenterOfMass().set(finalTransform.getCenterCoord());

        //reset all velocities when physics somehow broke
        if (this.isPhysicsBroken(calculations)) {
            this.ship.getShipData().setPhysicsEnabled(false);
            calculations.getLinearVelocity().zero();
            calculations.getAngularVelocity().zero();
        }

        this.ship.getShipTransformationManager().updatePreviousPhysicsTransform();
        this.ship.getShipTransformationManager().setCurrentPhysicsTransform(finalTransform);
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

    private void updateMassPropertiesIfNeeded(PhysicsCalculations calculations) {
        float mass = (float) Math.max(this.ship.getInertiaData().getGameTickMass(), 0.0001D);
        if (this.massPropertiesInitialized && !this.massPropertiesDirty
                && Math.abs(mass - this.lastAppliedMass) <= MASS_PROPERTY_EPSILON) return;
        PxRigidDynamic shipActor = (PxRigidDynamic) this.actor;

        //set mass properties
        Vector3d localCenterOfMass = getReferenceToCenterOfMass(this.ship, calculations.getPhysCenterOfMass());
        PxVec3 massLocalPose = PhysXActorUtil.toPxVec(localCenterOfMass);
        boolean updated = PxRigidBodyExt.setMassAndUpdateInertia(shipActor, mass, massLocalPose, false);
        massLocalPose.destroy();
        if (!updated) {
            shipActor.setMass(mass);
            setCenterOfMassPose((PxRigidDynamic) this.actor, this.ship, calculations.getPhysCenterOfMass());
            this.setMassInertia(calculations.getPhysMOITensor());
        }

        this.lastAppliedMass = mass;
        this.massPropertiesInitialized = true;
        this.massPropertiesDirty = false;
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
                                this.ship
                        );
                        if (forceVector == null) blockForce.zero();
                        else {
                            blockForce.x = forceVector.x();
                            blockForce.y = forceVector.y();
                            blockForce.z = forceVector.z();
                        }

                        Vector3dc otherPosition = blockForceProvider.getCustomBlockForcePosition(
                                world, mutablePos, state,
                                this.ship
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
                        if (torqueVector != null) calculations.addTorque(torqueVector);
                    }
                }
            }
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
        double drag = Math.pow(PhysicsCalculations.DRAG_CONSTANT, calculations.getPhysicsTimeDeltaPerPhysTick() * 20D);
        calculations.getLinearVelocity().mul(drag);
        calculations.getAngularVelocity().mul(drag);
    }

    //-----collision shape manipulation for ships starts here-----
    private void rebuildCollisionShapes() {
        this.clearShapes();
        this.shapesByBlock.clear();
        this.blocksByShape.clear();

        Vector3dc referencePosition = getReferencePositionInShipSpace(this.ship);
        boolean truncated = !this.attachShipCollisionShapes(this.ship.getBlockPositions(), referencePosition);

        if (truncated) {
            System.err.println("PhysX ship " + this.ship.getName() + " exceeded " + MAX_SHIP_SHAPES
                + " collision shapes; extra block shapes were skipped in this preliminary backend.");
        }
        this.lastBlockCount = this.ship.getBlockPositions().size();
    }

    private void updateCollisionShapes(Set<BlockPos> dirtyPositions) {
        Vector3dc referencePosition = getReferencePositionInShipSpace(this.ship);
        Set<BlockPos> affectedPositions = this.expandDirtyCollisionPositions(dirtyPositions);
        Set<PxShape> detachedShapes = Collections.newSetFromMap(new IdentityHashMap<>());

        for (BlockPos blockPos : new ArrayList<>(affectedPositions)) {
            affectedPositions.addAll(this.detachShipShapes(blockPos, detachedShapes));
        }

        boolean truncated = !this.attachShipCollisionShapes(affectedPositions, referencePosition);

        if (truncated) {
            System.err.println("PhysX ship " + this.ship.getName() + " exceeded " + MAX_SHIP_SHAPES
                + " collision shapes; some dirty block shapes were skipped in this preliminary backend.");
        }
        this.lastBlockCount = this.ship.getBlockPositions().size();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean attachShipCollisionShapes(Iterable<BlockPos> blockPositions, Vector3dc referencePosition) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        GreedyBlockMerger mergeableBlocks = new GreedyBlockMerger();
        List<ShipBlockCollisionData> separateBlocks = new ArrayList<>();

        for (BlockPos blockPos : blockPositions) {
            BlockPos immutablePos = blockPos.toImmutable();
            mutablePos.setPos(immutablePos);
            IBlockState state = this.getShipBlockState(this.ship, mutablePos);
            if (!this.isShipCollisionState(state)) continue;

            if (mergeableBlocks.isMergeableFullBlock(state)) {
                mergeableBlocks.add(immutablePos);
            }
            else separateBlocks.add(new ShipBlockCollisionData(immutablePos, state));
        }

        boolean complete = this.attachMergedShipShapes(mergeableBlocks, referencePosition);
        for (ShipBlockCollisionData block : separateBlocks) {
            if (this.getShapeCount() >= MAX_SHIP_SHAPES) return false;
            complete &= this.attachShipShapesForBlock(this.ship, block.pos(), block.state(), referencePosition);
        }
        return complete;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean attachMergedShipShapes(GreedyBlockMerger mergeableBlocks, Vector3dc referencePosition) {
        boolean[] complete = {true};
        mergeableBlocks.forEachMergedBlockBox((box, mergedBlocks) -> {
            if (this.getShapeCount() >= MAX_SHIP_SHAPES) {
                complete[0] = false;
                return;
            }

            PxShape shape = this.attachShipShape(box, referencePosition);
            if (shape != null) this.rememberShipShapeBlocks(shape, mergedBlocks);
        });
        return complete[0];
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean attachShipShapesForBlock(PhysicsObject ship, BlockPos pos, IBlockState state, Vector3dc referencePosition) {
        List<AxisAlignedBB> boxes = PhysXBlockSectionBody.getCollisionBoxes(ship.getWorld(), pos, state, false);
        for (AxisAlignedBB box : boxes) {
            if (this.getShapeCount() >= MAX_SHIP_SHAPES) return false;

            PxShape shape = this.attachShipShape(box, referencePosition);
            if (shape != null) this.rememberShipShapeBlocks(shape, List.of(pos.toImmutable()));
        }
        return true;
    }

    @Nullable
    private PxShape attachShipShape(AxisAlignedBB box, Vector3dc referencePosition) {
        PxShape shape = this.createBoxShape(box, this.material);
        if (shape == null) return null;

        PhysXActor.SHIP.setFilter(shape);
        double localX = (box.minX + box.maxX) * 0.5D - referencePosition.x();
        double localY = (box.minY + box.maxY) * 0.5D - referencePosition.y();
        double localZ = (box.minZ + box.maxZ) * 0.5D - referencePosition.z();
        PxTransform localPose = PhysXActorUtil.toPxTransform(localX, localY, localZ, new Quaterniond());
        shape.setLocalPose(localPose);
        localPose.destroy();

        if (!this.addShape(shape)) return null;
        return shape;
    }

    private void rememberShipShapeBlocks(PxShape shape, List<BlockPos> blockPositions) {
        List<BlockPos> immutablePositions = new ArrayList<>(blockPositions.size());
        for (BlockPos blockPosition : blockPositions) {
            BlockPos immutablePos = blockPosition.toImmutable();
            immutablePositions.add(immutablePos);
            this.shapesByBlock.computeIfAbsent(immutablePos, ignored -> new ArrayList<>()).add(shape);
        }
        this.blocksByShape.put(shape, immutablePositions);
    }

    private List<BlockPos> detachShipShapes(BlockPos pos, Set<PxShape> detachedShapes) {
        List<PxShape> blockShapes = this.shapesByBlock.get(pos);
        if (blockShapes == null) return List.of();

        List<BlockPos> affectedPositions = new ArrayList<>();
        for (PxShape shape : new ArrayList<>(blockShapes)) {
            if (detachedShapes.add(shape)) affectedPositions.addAll(this.detachShipShape(shape));
        }
        return affectedPositions;
    }

    private List<BlockPos> detachShipShape(PxShape shape) {
        List<BlockPos> blockPositions = this.blocksByShape.remove(shape);
        if (blockPositions == null) blockPositions = List.of();

        for (BlockPos blockPosition : blockPositions) {
            List<PxShape> blockShapes = this.shapesByBlock.get(blockPosition);
            if (blockShapes == null) continue;

            blockShapes.remove(shape);
            if (blockShapes.isEmpty()) this.shapesByBlock.remove(blockPosition);
        }

        this.releaseShape(shape);
        return blockPositions;
    }

    private Set<BlockPos> expandDirtyCollisionPositions(Set<BlockPos> dirtyPositions) {
        Set<BlockPos> expanded = new HashSet<>();
        for (BlockPos dirtyPosition : dirtyPositions) {
            int x = dirtyPosition.getX();
            int y = dirtyPosition.getY();
            int z = dirtyPosition.getZ();
            expanded.add(dirtyPosition.toImmutable());
            expanded.add(new BlockPos(x + 1, y, z));
            expanded.add(new BlockPos(x - 1, y, z));
            expanded.add(new BlockPos(x, y + 1, z));
            expanded.add(new BlockPos(x, y - 1, z));
            expanded.add(new BlockPos(x, y, z + 1));
            expanded.add(new BlockPos(x, y, z - 1));
        }
        return expanded;
    }
    //-----collision shape manipulation for ships ends here-----

    private void setMassInertia(Matrix3dc inertiaTensor) {
        PxRigidDynamic shipActor = (PxRigidDynamic) this.actor;
        float ix = (float) Math.max(inertiaTensor.m00(), 0.0001D);
        float iy = (float) Math.max(inertiaTensor.m11(), 0.0001D);
        float iz = (float) Math.max(inertiaTensor.m22(), 0.0001D);
        PxVec3 inertia = new PxVec3(ix, iy, iz);
        shipActor.setMassSpaceInertiaTensor(inertia);
        inertia.destroy();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean isShipCollisionState(IBlockState state) {
        if (state == null || state.getBlock() == Blocks.AIR || state.getMaterial() == Material.AIR) {
            return false;
        }
        return !PhysicsUtils.isLiquid(state) && state.getMaterial().blocksMovement();
    }

    private IBlockState getShipBlockState(PhysicsObject ship, BlockPos pos) {
        Chunk chunk = ship.getChunkAt(pos.getX() >> 4, pos.getZ() >> 4);
        if (chunk == null) return Blocks.AIR.getDefaultState();
        return chunk.getBlockState(pos);
    }

    private boolean isTouchingLiquidActor(AxisAlignedBB shipAabb, List<PhysXBlockSectionBody> liquidCollisionObjects) {
        for (PhysXBlockSectionBody blockSectionObject : liquidCollisionObjects) {
            if (blockSectionObject.isLiquidBlockIntersecting(shipAabb)) return true;
        }
        return false;
    }

    private double getSubmergedFraction(@NotNull World world, BlockPos.MutableBlockPos mutablePos, Vector3d centerWorld) {
        int x = (int) Math.floor(centerWorld.x);
        int z = (int) Math.floor(centerWorld.z);
        int minY = Math.max(0, (int) Math.floor(centerWorld.y - BLOCK_HALF_EXTENT - 0.25D));
        int maxY = Math.min(world.getHeight() - 1, (int) Math.floor(centerWorld.y + BLOCK_HALF_EXTENT));
        for (int y = maxY; y >= minY; y--) {
            mutablePos.setPos(x, y, z);
            if (PhysicsUtils.isLiquid(world.getBlockState(mutablePos))) {
                double waterSurfaceY = y + 1D;
                return Math.clamp(waterSurfaceY - (centerWorld.y - BLOCK_HALF_EXTENT), 0D, 1D);
            }
        }
        return 0D;
    }

    //---static helpers---
    private static void setCenterOfMassPose(@NotNull PxRigidDynamic actor, @NotNull PhysicsObject ship, @NotNull Vector3dc centerOfMass) {
        // Keep the actor frame anchored to the ship reference; only the PhysX mass frame follows COM.
        Vector3d localCenterOfMass = getReferenceToCenterOfMass(ship, centerOfMass);
        PxTransform centerOfMassPose = PhysXActorUtil.toPxTransform(
                localCenterOfMass.x,
                localCenterOfMass.y,
                localCenterOfMass.z,
                new Quaterniond()
        );
        actor.setCMassLocalPose(centerOfMassPose);
        centerOfMassPose.destroy();
    }

    private static Vector3d getReferenceToCenterOfMass(@NotNull PhysicsObject ship, @NotNull Vector3dc centerOfMass) {
        return new Vector3d(centerOfMass).sub(getReferencePositionInShipSpace(ship));
    }

    private static PxTransform createActorPose(@NotNull PhysicsObject ship, @NotNull ShipTransform transform) {
        Vector3d referencePosition = getReferencePositionInShipSpace(ship);
        transform.transformPosition(referencePosition, TransformType.SUBSPACE_TO_GLOBAL);
        return PhysXActorUtil.toPxTransform(
                referencePosition.x,
                referencePosition.y,
                referencePosition.z,
                transform.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL)
        );
    }

    private static Vector3d getReferencePositionInShipSpace(@NotNull PhysicsObject ship) {
        BlockPos reference = ship.getReferenceBlockPos();
        return new Vector3d(reference.getX(), reference.getY(), reference.getZ());
    }

    //---other classes---
    private record ShipBlockCollisionData(BlockPos pos, IBlockState state) {}

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
