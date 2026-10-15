package org.valkyrienskies.mod.common.physics;

import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jspecify.annotations.NonNull;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import valkyrienwarfare.api.IPhysicsEntity;
import valkyrienwarfare.api.TransformType;
import valkyrienwarfare.api.forces.IShipForces;

/**
 * Addon-facing ship physics API.
 * The ship blocks still live in shipyard chunks. This class tracks the shared rigid-body
 * state and per-tick force/torque accumulators that are consumed by the active physics backend.
 */
public class PhysicsCalculations implements IShipForces {
    public static final double DRAG_CONSTANT = 0.99D;
    public static final double EPSILON = 0.00000001;

    @NotNull
    private final PhysicsObject parent;

    public boolean actAsArchimedes = false; //omaga an archimedes ships reference
    @NotNull
    private final Vector3d physCenterOfMass;
    @NotNull
    private final Vector3d torque;
    @NotNull
    private final Vector3d force;
    @NotNull
    private final Vector3d pendingImpulse;
    @NotNull
    private final Vector3d pendingAngularImpulse;
    private double physTickTimeDelta;
    private final Matrix3d physMOITensor;
    private final Matrix3d physInvMOITensor;

    @NotNull
    private final Vector3d linearVelocity;
    @NotNull
    private final Vector3d angularVelocity;
    private boolean forceToUseGameTransform;

    public PhysicsCalculations(@NotNull PhysicsObject parent) {
        this.parent = parent;
        this.physMOITensor = new Matrix3d();
        this.physInvMOITensor = new Matrix3d();
        this.linearVelocity = new Vector3d(parent.getPhysicsData().getLinearVelocity());
        this.angularVelocity = new Vector3d(parent.getPhysicsData().getAngularVelocity());
        this.physCenterOfMass = new Vector3d();
        this.torque = new Vector3d();
        this.force = new Vector3d();
        this.pendingImpulse = new Vector3d();
        this.pendingAngularImpulse = new Vector3d();
        this.forceToUseGameTransform = false;
        this.generatePhysicsTransform();
    }

    //-----to be exposed in IShipForces-----
    @Override
    @NotNull
    public IPhysicsEntity getShip() {
        return this.parent;
    }

    @Override
    @NotNull
    public Vector3dc getWorldForce() {
        return new Vector3d(this.force);
    }

    @Override
    @NotNull
    public Vector3dc getWorldTorque() {
        return new Vector3d(this.torque);
    }

    @Override
    public double getTimeStepSeconds() {
        return this.physTickTimeDelta;
    }

    @Override
    public void applyWorldForce(@NonNull Vec3d force) {
        this.addForce(toVector(force));
    }

    @Override
    public void applyWorldTorque(@NonNull Vec3d torque) {
        this.addTorque(toVector(torque));
    }

    @Override
    public void applyWorldForceAtWorldPosition(@NonNull Vec3d force, @NonNull Vec3d worldPosition) {
        ShipTransform transform = this.parent.getShipTransformationManager().getCurrentPhysicsTransform();
        Vector3d relativePosition = new Vector3d(
                worldPosition.x - transform.getPosX(),
                worldPosition.y - transform.getPosY(),
                worldPosition.z - transform.getPosZ()
        );
        this.addForceAtPoint(relativePosition, toVector(force), new Vector3d());
    }

    @Override
    public void applyShipForce(@NonNull Vec3d force) {
        this.addForce(this.toWorldDirection(force));
    }

    @Override
    public void applyShipTorque(@NonNull Vec3d torque) {
        this.addTorque(this.toWorldDirection(torque));
    }

    @Override
    public void applyWorldForceAtShipPosition(@NonNull Vec3d force, @NonNull Vec3d shipPosition) {
        Vector3d relativePosition = this.shipPositionRelativeToCenter(shipPosition);
        this.addForceAtPoint(relativePosition, toVector(force), new Vector3d());
    }

    @Override
    public void applyShipForceAtShipPosition(@NonNull Vec3d force, @NonNull Vec3d shipPosition) {
        Vector3d relativePosition = this.shipPositionRelativeToCenter(shipPosition);
        this.addForceAtPoint(relativePosition, toWorldDirection(force), new Vector3d());
    }

    @Override
    public void applyBuoyancy(double force) {
        this.applyBuoyancy(force, new Vec3d(this.physCenterOfMass.x, this.physCenterOfMass.y, this.physCenterOfMass.z));
    }

    @Override
    public void applyBuoyancy(double force, @NonNull Vec3d shipPosition) {
        Vector3d worldPosition = toVector(shipPosition);
        this.parent.getShipTransformationManager().getCurrentPhysicsTransform().transformPosition(worldPosition, TransformType.SUBSPACE_TO_GLOBAL);

        double submergedFraction = this.getSubmergedFraction(worldPosition);
        if (submergedFraction <= 0D) return;

        Vector3d relativePosition = this.shipPositionRelativeToCenter(shipPosition);
        Vector3d buoyancyForce = new Vector3d(0D, force * submergedFraction, 0D);
        this.addForceAtPoint(relativePosition, buoyancyForce, new Vector3d());
    }

    @NotNull
    private Vector3d toWorldDirection(Vec3d direction) {
        Vector3d worldDirection = toVector(direction);
        this.parent.getShipTransformationManager().getCurrentPhysicsTransform()
                .transformDirection(worldDirection, TransformType.SUBSPACE_TO_GLOBAL);
        return worldDirection;
    }

    @NotNull
    private Vector3d shipPositionRelativeToCenter(Vec3d shipPosition) {
        Vector3d relativePosition = new Vector3d(shipPosition.x, shipPosition.y, shipPosition.z);
        relativePosition.sub(this.physCenterOfMass);
        this.parent.getShipTransformationManager().getCurrentPhysicsTransform()
                .transformDirection(relativePosition, TransformType.SUBSPACE_TO_GLOBAL);
        return relativePosition;
    }

    private double getSubmergedFraction(@NotNull Vector3dc worldPosition) {
        World world = this.parent.getWorld();
        int x = (int) Math.floor(worldPosition.x());
        int z = (int) Math.floor(worldPosition.z());
        int minY = Math.max(0, (int) Math.floor(worldPosition.y() - 0.75D));
        int maxY = Math.min(world.getHeight() - 1, (int) Math.floor(worldPosition.y() + 0.5D));
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        for (int y = maxY; y >= minY; y--) {
            mutablePos.setPos(x, y, z);
            if (PhysicsUtils.isLiquid(world.getBlockState(mutablePos))) {
                return Math.clamp(y + 1D - (worldPosition.y() - 0.5D), 0D, 1D);
            }
        }
        return 0D;
    }

    @NotNull
    private static Vector3d toVector(Vec3d vector) {
        return new Vector3d(vector.x, vector.y, vector.z);
    }

    //-----other methods-----
    public void generatePhysicsTransform() {
        ShipTransform parentTransform = this.getParent().getShipData().getShipTransform();
        Quaterniond physicsRotation = parentTransform.getSubspaceToGlobal().getNormalizedRotation(new Quaterniond());
        this.physCenterOfMass.set(parentTransform.getCenterCoord());
        ShipTransform physicsTransform = new ShipTransform(
                parentTransform.getPosX(),
                parentTransform.getPosY(),
                parentTransform.getPosZ(),
                physicsRotation,
                new Vector3d(this.physCenterOfMass)
        );
        this.getParent().getShipTransformationManager().setCurrentPhysicsTransform(physicsTransform);
        this.getParent().getShipTransformationManager().updatePreviousPhysicsTransform();
    }

    public void addForceAtPoint(Vector3dc posRelToShipCenter, Vector3dc forceToApply, Vector3d crossVector) {
        posRelToShipCenter.cross(forceToApply, crossVector);
        this.addTorque(crossVector);
        this.addForce(forceToApply);
    }

    /**
     * Queues an instantaneous impulse and its resulting angular impulse for the next physics tick.
     * For now, it's for the built-in boat oaring feature only, but I might expose this to the API...
     */
    public void addImpulseAtPoint(Vector3dc posRelToShipCenter, Vector3dc impulseToApply, Vector3d crossVector) {
        posRelToShipCenter.cross(impulseToApply, crossVector);
        this.pendingAngularImpulse.add(crossVector);
        this.pendingImpulse.add(impulseToApply);
    }

    public Vector3d getVelocityAtPoint(Vector3dc posRelativeToShipCenter) {
        return this.getVelocityAtPoint(posRelativeToShipCenter, new Vector3d());
    }

    public Vector3d getVelocityAtPoint(Vector3dc posRelativeToShipCenter, Vector3d dest) {
        Vector3d velocityAtPoint = this.getAngularVelocity().cross(posRelativeToShipCenter, dest);
        velocityAtPoint.add(this.getLinearVelocity());
        return velocityAtPoint;
    }

    public double getInvMass() {
        return 1.0D / Math.max(this.parent.getInertiaData().getGameTickMass(), 0.0001D);
    }

    public double getPhysicsTimeDeltaPerPhysTick() {
        return this.physTickTimeDelta;
    }

    public void setPhysicsTimeDeltaPerPhysTick(double physTickTimeDelta) {
        this.physTickTimeDelta = physTickTimeDelta;
    }

    public Matrix3d getPhysInvMOITensor() {
        return this.physInvMOITensor;
    }

    public Matrix3d getPhysMOITensor() {
        return this.physMOITensor;
    }

    @NotNull
    public PhysicsObject getParent() {
        return this.parent;
    }

    public void addForce(Vector3dc addedForce) {
        this.force.add(addedForce);
    }

    public void addTorque(Vector3dc addedTorque) {
        this.torque.add(addedTorque);
    }

    public void resetForceAndTorque() {
        this.force.zero();
        this.torque.zero();
    }

    public void drainForceAndTorque(@NotNull Vector3d forceDest, @NotNull Vector3d torqueDest) {
        forceDest.set(this.force).mul(this.physTickTimeDelta).add(this.pendingImpulse);
        torqueDest.set(this.torque).mul(this.physTickTimeDelta).add(this.pendingAngularImpulse);
        this.pendingImpulse.zero();
        this.pendingAngularImpulse.zero();
        this.resetForceAndTorque();
    }

    @NotNull
    public Vector3d getPhysCenterOfMass() {
        return this.physCenterOfMass;
    }

    @NotNull
    public Vector3d getLinearVelocity() {
        return this.linearVelocity;
    }

    @NotNull
    public Vector3d getAngularVelocity() {
        return this.angularVelocity;
    }

    public boolean setForceToUseGameTransform(boolean forceToUseGameTransform) {
        boolean previousValue = this.forceToUseGameTransform;
        this.forceToUseGameTransform = forceToUseGameTransform;
        return previousValue;
    }
}
