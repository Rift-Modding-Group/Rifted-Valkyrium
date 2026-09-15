package org.valkyrienskies.mod.common.physics;

import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3d;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

/**
 * Addon-facing ship physics API.
 * The ship blocks still live in shipyard chunks. This class tracks the shared rigid-body
 * state and per-tick force/torque accumulators that are consumed by the active physics backend.
 */
public class PhysicsCalculations {
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
    private Vector3d pendingImpulse;
    @NotNull
    private Vector3d pendingAngularImpulse;
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
     */
    public void addImpulseAtPoint(Vector3dc posRelToShipCenter, Vector3dc impulseToApply, Vector3d crossVector) {
        posRelToShipCenter.cross(impulseToApply, crossVector);
        this.pendingAngularImpulse.add(crossVector);
        this.pendingImpulse.add(impulseToApply);
    }

    /**
     * Moves queued impulses into the active accumulators after their per-tick reset.
     */
    public void applyPendingImpulses() {
        this.force.add(this.pendingImpulse);
        this.torque.add(this.pendingAngularImpulse);
        this.pendingImpulse.zero();
        this.pendingAngularImpulse.zero();
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

    public void addForce(final Vector3dc addedForce) {
        final double timeStep = this.getPhysicsTimeDeltaPerPhysTick();
        this.force.add(addedForce.x() * timeStep, addedForce.y() * timeStep, addedForce.z() * timeStep);
    }

    public void addTorque(final Vector3dc addedTorque) {
        final double timeStep = this.getPhysicsTimeDeltaPerPhysTick();
        this.torque.add(addedTorque.x() * timeStep, addedTorque.y() * timeStep, addedTorque.z() * timeStep);
    }

    public void resetForceAndTorque() {
        this.force.zero();
        this.torque.zero();
    }

    public void drainForceAndTorque(@NotNull Vector3d forceDest, @NotNull Vector3d torqueDest) {
        forceDest.set(this.force);
        torqueDest.set(this.torque);
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
