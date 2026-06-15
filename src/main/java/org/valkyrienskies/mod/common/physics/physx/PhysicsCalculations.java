package org.valkyrienskies.mod.common.physics.physx;

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

    private final PhysicsObject parent;

    public boolean actAsArchimedes = false; //omaga an archimedes ships reference
    private final Vector3d physCenterOfMass;
    private final Vector3d torque;
    private final Vector3d force;
    private double physTickTimeDelta;
    private final Matrix3d physMOITensor;
    private final Matrix3d physInvMOITensor;

    private final Vector3d linearVelocity;
    private final Vector3d angularVelocity;
    private boolean forceToUseGameTransform;

    public PhysicsCalculations(PhysicsObject parent) {
        this.parent = parent;
        this.physMOITensor = new Matrix3d();
        this.physInvMOITensor = new Matrix3d();
        this.linearVelocity = new Vector3d(parent.getPhysicsData().getLinearVelocity());
        this.angularVelocity = new Vector3d(parent.getPhysicsData().getAngularVelocity());
        this.physCenterOfMass = new Vector3d();
        this.torque = new Vector3d();
        this.force = new Vector3d();
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

    @Deprecated
    public void addForceAtPoint(Vector3dc inBodyWO, Vector3dc forceToApply) {
        addForceAtPoint(inBodyWO, forceToApply, new Vector3d());
    }

    @Deprecated
    public void addForceAtPoint(Vector3dc inBodyWO, Vector3dc forceToApply, Vector3d crossVector) {
        inBodyWO.cross(forceToApply, crossVector);
        this.torque.add(crossVector);
        this.force.add(forceToApply);
    }

    public void addForceAtPointNew(Vector3dc posRelToShipCenter, Vector3dc forceToApply, Vector3d tempStorage) {
        final double timeStep = getPhysicsTimeDeltaPerPhysTick();
        posRelToShipCenter.cross(forceToApply, tempStorage);
        this.torque.add(tempStorage.x() * timeStep, tempStorage.y() * timeStep, tempStorage.z() * timeStep);
        this.force.add(forceToApply.x() * timeStep, forceToApply.y() * timeStep, forceToApply.z() * timeStep);
    }

    @Deprecated
    public Vector3d getVelocityAtPoint(Vector3dc posRelativeToShipCenter) {
        Vector3d speed = getAngularVelocity().cross(posRelativeToShipCenter, new Vector3d());
        speed.add(getLinearVelocity());
        return speed;
    }

    public Vector3d getVelocityAtPoint(Vector3dc posRelativeToShipCenter, Vector3d dest) {
        Vector3d velocityAtPoint = getAngularVelocity().cross(posRelativeToShipCenter, dest);
        velocityAtPoint.add(getLinearVelocity());
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

    public PhysicsObject getParent() {
        return this.parent;
    }

    public double getInertiaAlongRotationAxis() {
        Vector3d rotationAxis = new Vector3d(getAngularVelocity());
        rotationAxis.normalize();
        getPhysMOITensor().transform(rotationAxis);
        return rotationAxis.length();
    }

    public void addForceAndTorque(final Vector3dc addedForce, final Vector3dc addedTorque) {
        final double timeStep = getPhysicsTimeDeltaPerPhysTick();
        this.force.add(addedForce.x() * timeStep, addedForce.y() * timeStep, addedForce.z() * timeStep);
        this.torque.add(addedTorque.x() * timeStep, addedTorque.y() * timeStep, addedTorque.z() * timeStep);
    }

    public Vector3d getPhysCenterOfMass() {
        return this.physCenterOfMass;
    }

    public Vector3d getLinearVelocity() {
        return this.linearVelocity;
    }

    public Vector3d getAngularVelocity() {
        return this.angularVelocity;
    }

    public Vector3d getForce() {
        return this.force;
    }

    public Vector3d getTorque() {
        return this.torque;
    }

    public boolean setForceToUseGameTransform(boolean forceToUseGameTransform) {
        boolean previousValue = this.forceToUseGameTransform;
        this.forceToUseGameTransform = forceToUseGameTransform;
        return previousValue;
    }
}
