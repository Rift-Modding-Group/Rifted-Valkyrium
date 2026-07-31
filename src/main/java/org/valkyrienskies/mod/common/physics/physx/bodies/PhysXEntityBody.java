package org.valkyrienskies.mod.common.physics.physx.bodies;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.bodies.IPhysicsEntityBody;
import org.valkyrienskies.mod.common.physics.PhysicsEntityMovementQueue;
import org.valkyrienskies.mod.common.physics.PhysicsEntitySnapshot;
import org.valkyrienskies.mod.common.physics.physx.PhysXActor;
import org.valkyrienskies.mod.common.physics.physx.PhysXActorUtil;
import physx.common.PxTransform;
import physx.common.PxVec3;
import physx.extensions.PxRigidBodyExt;
import physx.physics.*;

import java.util.List;

/**
 * for entities
 */
public class PhysXEntityBody extends AbstractPhysXCollisionObject<PhysXEntityBody.Identifier, PhysicsEntitySnapshot> implements IPhysicsEntityBody {
    private static final double ENTITY_SHAPE_SIZE_EPSILON = 1.0E-6D;
    private static final double POSITION_EPSILON_SQUARED = 1.0E-10D;
    private static final double MAX_COLLISION_STEP = 4D;
    private static final int SUPPORT_MISS_SNAPSHOTS = 2;
    private static final double INTENTIONAL_SEPARATION_VELOCITY = 0.08D;
    private static final float ENTITY_MASS = 80f;

    @NotNull
    private final PxMaterial material;
    @NotNull
    private final Entity entity;
    @NotNull
    private final PhysicsEntityMovementQueue physicsEntityMovementQueue;
    @NotNull
    private final Object movementLock = new Object();
    @NotNull
    private final Vector3d preSimulationCenter = new Vector3d();
    @NotNull
    private final Vector3d preSimulationVelocity = new Vector3d();
    @NotNull
    private final Vector3d preSimulationSupportPoint = new Vector3d();
    @NotNull
    private final Vector3d preSimulationShipPosition = new Vector3d();
    @NotNull
    private final Quaterniond preSimulationShipRotation = new Quaterniond();
    @NotNull
    private final Vector3d postSimulationShipPosition = new Vector3d();
    @NotNull
    private final Quaterniond postSimulationShipRotation = new Quaterniond();
    @NotNull
    private final Vector3d lastExternalCenter = new Vector3d();
    private final long movementEpoch;

    private double shapeSizeX;
    private double shapeSizeY;
    private double shapeSizeZ;
    private boolean hasCachedShapeSize;
    private boolean hasExternalCenter;
    private PhysicsEntitySnapshot entitySnapshot;
    private PhysicsEntitySnapshot lastProcessedSnapshot;
    private PhysicsEntitySnapshot lastSupportSnapshot;
    @Nullable
    private PhysXShipBody supportingShipBody;
    @Nullable
    private PhysXShipBody simulationSupportingShipBody;
    private int supportMissSnapshots;
    private double timeStep;

    public PhysXEntityBody(
            @NotNull PhysXEntityBody.Identifier identifier, @NotNull PxPhysics physics, @NotNull PxScene scene,
            @NotNull PxMaterial material, @NotNull PhysicsEntityMovementQueue physicsEntityMovementQueue,
            @NotNull PhysicsEntitySnapshot entitySnapshot
    ) {
        super(identifier, physics, scene, () -> {
            AxisAlignedBB entityAABB = entitySnapshot.boundingBox();
            PxTransform transform = PhysXActorUtil.createTransform(
                    (entityAABB.minX + entityAABB.maxX) * 0.5D,
                    (entityAABB.minY + entityAABB.maxY) * 0.5D,
                    (entityAABB.minZ + entityAABB.maxZ) * 0.5D
            );
            PxRigidDynamic toReturn = physics.createRigidDynamic(transform);
            transform.destroy();

            toReturn.setRigidBodyFlag(PxRigidBodyFlagEnum.eKINEMATIC, false);
            toReturn.setRigidBodyFlag(PxRigidBodyFlagEnum.eENABLE_CCD, true);
            toReturn.setRigidBodyFlag(PxRigidBodyFlagEnum.eENABLE_CCD_FRICTION, true);
            toReturn.setRigidDynamicLockFlag(PxRigidDynamicLockFlagEnum.eLOCK_ANGULAR_X, true);
            toReturn.setRigidDynamicLockFlag(PxRigidDynamicLockFlagEnum.eLOCK_ANGULAR_Y, true);
            toReturn.setRigidDynamicLockFlag(PxRigidDynamicLockFlagEnum.eLOCK_ANGULAR_Z, true);
            toReturn.setSolverIterationCounts(8, 2);

            return toReturn;
        });
        this.material = material;
        this.physicsEntityMovementQueue = physicsEntityMovementQueue;
        this.entity = entitySnapshot.entity();
        this.entitySnapshot = entitySnapshot;
        this.rebuildShape(entitySnapshot.boundingBox());
        this.movementEpoch = this.physicsEntityMovementQueue.register(this, this.entity);
    }

    @Override
    public void synchronize(@NotNull PhysicsEntitySnapshot entitySnapshot) {
        if (entitySnapshot.entity() == this.entity) this.entitySnapshot = entitySnapshot;
    }

    public void updateSupportState(@NotNull PhysicsEntitySnapshot snapshot, @Nullable PhysXShipBody contactShipBody) {
        // The physics backend can consume one game snapshot over several substeps.
        // Count contact misses once per game snapshot, not once per physics frame.
        if (snapshot == this.lastSupportSnapshot) return;
        this.lastSupportSnapshot = snapshot;

        if (contactShipBody != null && snapshot.standingOnSupportingShip()) {
            this.supportMissSnapshots = 0;
            this.setSupportingShipBody(contactShipBody);
            return;
        }

        if (this.supportingShipBody == null) {
            this.supportMissSnapshots = 0;
            return;
        }

        // A definite different ship invalidates the old lease. A null contact is
        // only a missed probe and must use the miss counter below; clearing it
        // immediately made support flicker while walking or rotating.
        if (contactShipBody != null && contactShipBody != this.supportingShipBody) {
            this.supportMissSnapshots = 0;
            this.setSupportingShipBody(null);
            return;
        }

        if (this.isIntentionalSupportSeparation(snapshot.onGround(), snapshot.verticalVelocity()) || ++this.supportMissSnapshots > SUPPORT_MISS_SNAPSHOTS) {
            this.supportMissSnapshots = 0;
            this.setSupportingShipBody(null);
        }
    }

    @Override
    public void updateBeforeSimulation(
            @NotNull World hostWorld,
            @NotNull List<PhysXBlockSectionBody> blockSectionsWithLiquids,
            double timeStep
    ) {
        this.timeStep = timeStep;
        PhysicsEntitySnapshot snapshot = this.entitySnapshot;
        this.rebuildShape(snapshot.boundingBox());
        this.reconcileExternalMovement(snapshot);

        PxTransform pose = this.actor.getGlobalPose();
        PxRigidDynamic entityActor = (PxRigidDynamic) this.actor;

        this.preSimulationCenter.set(PhysXActorUtil.fromPxVec(pose.getP()));
        this.preSimulationSupportPoint.set(
                this.preSimulationCenter.x,
                this.preSimulationCenter.y - (snapshot.boundingBox().maxY - snapshot.boundingBox().minY) * 0.5D,
                this.preSimulationCenter.z
        );

        this.simulationSupportingShipBody = this.supportingShipBody;
        if (this.simulationSupportingShipBody != null) {
            this.simulationSupportingShipBody.copyActorPose(
                    this.preSimulationShipPosition,
                    this.preSimulationShipRotation
            );
        }
        this.preSimulationVelocity.set(PhysXActorUtil.fromPxVec(entityActor.getLinearVelocity()));

        PxVec3 zeroVelocity = new PxVec3(0f, 0f, 0f);
        entityActor.setAngularVelocity(zeroVelocity, true);
        zeroVelocity.destroy();
    }

    @Override
    public void updateAfterSimulation() {
        PxRigidDynamic entityActor = (PxRigidDynamic) this.actor;
        PxTransform pose = this.actor.getGlobalPose();
        Vector3d simulatedCenter = PhysXActorUtil.fromPxVec(pose.getP());
        Vector3d collisionStep = simulatedCenter.sub(this.preSimulationCenter, new Vector3d());
        PxVec3 finalVelocity = entityActor.getLinearVelocity();

        if (this.simulationSupportingShipBody != null) {
            this.simulationSupportingShipBody.copyActorPose(this.postSimulationShipPosition, this.postSimulationShipRotation);
            //calculate rigid pose displacement
            collisionStep.set(this.preSimulationSupportPoint).sub(this.preSimulationShipPosition);
            new Quaterniond(this.preSimulationShipRotation).conjugate().transform(collisionStep);
            this.postSimulationShipRotation.transform(collisionStep);
            collisionStep.add(this.postSimulationShipPosition).sub(this.preSimulationSupportPoint);

            if (!collisionStep.isFinite() || (collisionStep.lengthSquared() > MAX_COLLISION_STEP * MAX_COLLISION_STEP)) {
                collisionStep.zero();
                this.clearLinearVelocity();
            }
            else {
                Vector3d correctedCenter = this.preSimulationCenter.add(collisionStep, new Vector3d());
                this.forcePose(correctedCenter);
                Vector3d supportVelocity = this.timeStep > 0D ? collisionStep.div(this.timeStep, new Vector3d()) : new Vector3d();
                PxVec3 supportPhysXVelocity = PhysXActorUtil.toPxVec(supportVelocity);
                entityActor.setLinearVelocity(supportPhysXVelocity, true);
                supportPhysXVelocity.destroy();
            }
        }
        else {
            if (!collisionStep.isFinite() || (collisionStep.lengthSquared() > MAX_COLLISION_STEP * MAX_COLLISION_STEP)) {
                collisionStep.zero();
                this.clearLinearVelocity();
                finalVelocity = entityActor.getLinearVelocity();
            }

            // Keep velocity acquired from ship contacts across substeps. This is what lets
            // tangential velocity from a rotating ship continue carrying the entity instead
            // of requiring friction to accelerate it again from rest every physics frame.
            Vector3d retainedVelocity = PhysXActorUtil.fromPxVec(finalVelocity);
            double gravityX = VSConfig.doGravity ? VSConfig.gravityVecX : 0D;
            double gravityY = VSConfig.doGravity ? VSConfig.gravityVecY : 0D;
            double gravityZ = VSConfig.doGravity ? VSConfig.gravityVecZ : 0D;
            AxisMotion xMotion = this.removeGravityOnlyMotion(collisionStep.x, this.preSimulationVelocity.x, finalVelocity.getX(), gravityX, this.timeStep);
            AxisMotion yMotion = this.removeGravityOnlyMotion(collisionStep.y, this.preSimulationVelocity.y, finalVelocity.getY(), gravityY, this.timeStep);
            AxisMotion zMotion = this.removeGravityOnlyMotion(collisionStep.z, this.preSimulationVelocity.z, finalVelocity.getZ(), gravityZ, this.timeStep);
            collisionStep.set(xMotion.displacement, yMotion.displacement, zMotion.displacement);
            retainedVelocity.set(xMotion.velocity, yMotion.velocity, zMotion.velocity);

            Vector3d correctedCenter = this.preSimulationCenter.add(collisionStep, new Vector3d());
            if (correctedCenter.distanceSquared(simulatedCenter) > POSITION_EPSILON_SQUARED) {
                this.forcePose(correctedCenter);
            }
            PxVec3 retainedPhysXVelocity = PhysXActorUtil.toPxVec(retainedVelocity);
            entityActor.setLinearVelocity(retainedPhysXVelocity, true);
            retainedPhysXVelocity.destroy();

            if (collisionStep.lengthSquared() > POSITION_EPSILON_SQUARED) {
                this.physicsEntityMovementQueue.queue(this, this.entity, collisionStep);
            }
        }
    }

    /**
     * called by PhysicsEntityMovementQueue on the Minecraft thread.
     */
    @Nullable
    @Override
    public Vector3d applyPendingMovement(@NotNull Matrix4dc movementTransform) {
        synchronized (this.movementLock) {
            if (this.released || this.entity.isDead || this.entity.world != this.identifier.world || !movementTransform.isFinite()) {
                return null;
            }

            Vector3d newPosition = movementTransform.transformPosition(new Vector3d(this.entity.posX, this.entity.posY, this.entity.posZ));
            if (!newPosition.isFinite()) return null;

            double displacementX = newPosition.x - this.entity.posX;
            double displacementY = newPosition.y - this.entity.posY;
            double displacementZ = newPosition.z - this.entity.posZ;

            if (displacementX * displacementX + displacementY * displacementY + displacementZ * displacementZ > MAX_COLLISION_STEP * MAX_COLLISION_STEP) {
                this.clearLinearVelocity();
                return null;
            }

            this.entity.setPosition(newPosition.x, newPosition.y, newPosition.z);
            return new Vector3d(displacementX, displacementY, displacementZ);
        }
    }

    @Override
    public void release() {
        if (this.released) return;
        this.physicsEntityMovementQueue.remove(this, this.entity);
        super.release();
    }

    /**
     * rebuild shape when the entity somehow changes in size
     * */
    private void rebuildShape(@NotNull AxisAlignedBB entityBox) {
        double sizeX = entityBox.maxX - entityBox.minX;
        double sizeY = entityBox.maxY - entityBox.minY;
        double sizeZ = entityBox.maxZ - entityBox.minZ;
        if (this.hasShapes() && this.hasCachedShapeSize && this.hasSameShapeSize(sizeX, sizeY, sizeZ)) {
            return;
        }

        if (this.hasShapes()) {
            this.clearShapes();
            this.clearCachedShapeSize();
        }

        PxShape shape = this.createBoxShape(entityBox, this.material);
        if (shape == null) return;

        PhysXActor.ENTITY.setFilter(shape);
        if (!this.addShape(shape)) return;

        this.shapeSizeX = sizeX;
        this.shapeSizeY = sizeY;
        this.shapeSizeZ = sizeZ;
        this.hasCachedShapeSize = true;
        PxRigidDynamic entityActor = (PxRigidDynamic) this.actor;
        if (!PxRigidBodyExt.updateMassAndInertia(entityActor, ENTITY_MASS)) {
            entityActor.setMass(ENTITY_MASS);
        }
    }

    private boolean hasSameShapeSize(double sizeX, double sizeY, double sizeZ) {
        return Math.abs(this.shapeSizeX - sizeX) <= ENTITY_SHAPE_SIZE_EPSILON
                && Math.abs(this.shapeSizeY - sizeY) <= ENTITY_SHAPE_SIZE_EPSILON
                && Math.abs(this.shapeSizeZ - sizeZ) <= ENTITY_SHAPE_SIZE_EPSILON;
    }

    private void clearCachedShapeSize() {
        this.shapeSizeX = 0D;
        this.shapeSizeY = 0D;
        this.shapeSizeZ = 0D;
        this.hasCachedShapeSize = false;
    }

    private void reconcileExternalMovement(@NotNull PhysicsEntitySnapshot snapshot) {
        if (snapshot == this.lastProcessedSnapshot) return;

        AxisAlignedBB snapshotBox = snapshot.boundingBox();
        Vector3d snapshotCenter = new Vector3d(
                (snapshotBox.minX + snapshotBox.maxX) * 0.5D,
                (snapshotBox.minY + snapshotBox.maxY) * 0.5D,
                (snapshotBox.minZ + snapshotBox.maxZ) * 0.5D
        );
        PhysicsEntityMovementQueue.MovementMarker movementMarker = snapshot.movementMarker();
        Vector3d externalCenter = this.calculateExternalCenter(
                snapshotCenter,
                movementMarker,
                this.movementEpoch
        );

        if (this.hasExternalCenter) {
            Vector3d externalDisplacement = externalCenter.sub(this.lastExternalCenter, new Vector3d());
            if (externalDisplacement.isFinite() && externalDisplacement.lengthSquared() > POSITION_EPSILON_SQUARED) {
                PxTransform currentPose = this.actor.getGlobalPose();
                Vector3d correctedCenter = PhysXActorUtil.fromPxVec(currentPose.getP()).add(externalDisplacement);
                this.forcePose(correctedCenter);
                if (externalDisplacement.lengthSquared() > MAX_COLLISION_STEP * MAX_COLLISION_STEP) {
                    this.clearLinearVelocity();
                }
            }
        }

        this.lastExternalCenter.set(externalCenter);
        this.hasExternalCenter = true;
        this.lastProcessedSnapshot = snapshot;
    }

    @NotNull
    private Vector3d calculateExternalCenter(
            @NotNull Vector3dc snapshotCenter,
            @NotNull PhysicsEntityMovementQueue.MovementMarker movementMarker,
            long movementEpoch
    ) {
        if (movementMarker.epoch() == movementEpoch) {
            return snapshotCenter.sub(movementMarker.appliedDisplacement(), new Vector3d());
        }

        // The first snapshot normally predates body registration. Treat it as
        // the baseline rather than subtracting movement from another body.
        return new Vector3d(snapshotCenter);
    }

    private AxisMotion removeGravityOnlyMotion(
            double displacement, double initialVelocity, double finalVelocity,
            double gravity, double timeStep
    ) {
        if (gravity == 0D) return new AxisMotion(displacement, finalVelocity);

        double gravityVelocityDelta = gravity * timeStep;
        double gravityOnlyVelocity = initialVelocity + gravityVelocityDelta;
        double tolerance = Math.max(1.0E-3D, Math.abs(gravityVelocityDelta) * 0.05D);
        if (Math.abs(finalVelocity - gravityOnlyVelocity) > tolerance) {
            return new AxisMotion(displacement, finalVelocity);
        }

        // PhysX uses semi-implicit integration for rigid bodies. Remove only the
        // gravity contribution; velocity inherited from a ship remains intact.
        return new AxisMotion(
                displacement - gravity * timeStep * timeStep,
                initialVelocity
        );
    }

    private boolean isIntentionalSupportSeparation(boolean onGround, double verticalVelocity) {
        return !onGround && verticalVelocity > INTENTIONAL_SEPARATION_VELOCITY;
    }

    private void clearLinearVelocity() {
        PxVec3 zeroVelocity = new PxVec3(0f, 0f, 0f);
        PxRigidDynamic entityActor = (PxRigidDynamic) this.actor;
        entityActor.setLinearVelocity(zeroVelocity, true);
        zeroVelocity.destroy();
    }

    private void setSupportingShipBody(@Nullable PhysXShipBody supportingShipBody) {
        if (this.supportingShipBody == supportingShipBody) return;

        this.supportingShipBody = supportingShipBody;
        this.physicsEntityMovementQueue.setSupportingShip(
                this,
                this.entity,
                supportingShipBody == null ? null : supportingShipBody.getShip().getShipData().getUuid()
        );
    }

    private void forcePose(@NotNull Vector3dc center) {
        PxTransform transform = PhysXActorUtil.createTransform(center.x(), center.y(), center.z());
        this.actor.setGlobalPose(transform, true);
        transform.destroy();
    }

    //---other classes---
    //helper to store angular motion
    private record AxisMotion(double displacement, double velocity) {}

    public static final class Identifier extends AbstractPhysXCollisionObject.Identifier {
        @NotNull
        private final World world;
        @NotNull
        private final Entity entity;

        public Identifier(@NotNull Entity entity) {
            this.world = entity.world;
            this.entity = entity;
        }

        @Override
        public boolean equals(Object object) {
            if (this == object) return true;
            if (!(object instanceof Identifier that)) return false;
            return this.world == that.world && this.entity == that.entity;
        }

        @Override
        public int hashCode() {
            return 31 * System.identityHashCode(this.world) + System.identityHashCode(this.entity);
        }
    }
}
