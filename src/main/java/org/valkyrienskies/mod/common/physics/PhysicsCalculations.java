package org.valkyrienskies.mod.common.physics;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Matrix3dc;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.block.IBlockForceProvider;
import org.valkyrienskies.mod.common.block.IBlockTorqueProvider;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.physics.collision.PhysXShipBody;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.ShipPilot;
import valkyrienwarfare.api.TransformType;

import java.util.LinkedList;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.SortedMap;
import java.util.TreeMap;

/**
 * Addon-facing ship physics API backed by PhysX rigid bodies.
 * <p>
 * The ship blocks still live in shipyard chunks. This class only tracks the projected rigid-body
 * state and per-tick impulses that are drained into {@link PhysXShipBody} before scene simulation.
 */
public class PhysicsCalculations {
    public static final double DRAG_CONSTANT = 0.99D;
    public static final double EPSILON = 0.00000001;

    private final PhysicsObject parent;

    public boolean actAsArchimedes = false; //omaga an archimedes ships reference
    private Vector3dc physCenterOfMass;
    private final Vector3d torque;
    private final Vector3d force;
    private double physTickMass;
    private double physTickTimeDelta;
    private Matrix3dc physMOITensor;
    private Matrix3dc physInvMOITensor;
    private Quaterniondc physRotation;
    private double physX, physY, physZ;

    private final Vector3d linearVelocity;
    private final Vector3d angularVelocity;
    private boolean forceToUseGameTransform;
    private boolean centerOfMassPoseDirty;

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
        this.centerOfMassPoseDirty = false;
        generatePhysicsTransform();
    }

    public void generatePhysicsTransform() {
        ShipTransform parentTransform = this.getParent().getShipData().getShipTransform();
        this.physRotation = parentTransform.getSubspaceToGlobal().getNormalizedRotation(new Quaterniond());
        this.physX = parentTransform.getPosX();
        this.physY = parentTransform.getPosY();
        this.physZ = parentTransform.getPosZ();
        this.physCenterOfMass = parentTransform.getCenterCoord();
        ShipTransform physicsTransform = new ShipTransform(this.physX, this.physY, this.physZ, this.physRotation, this.physCenterOfMass);
        this.getParent().getShipTransformationManager().setCurrentPhysicsTransform(physicsTransform);
        this.getParent().getShipTransformationManager().updatePreviousPhysicsTransform();
    }

    public void prePhysXTick(double physTickTimeDelta) {
        this.force.zero();
        this.torque.zero();
        updatePhysSpeedAndIters(physTickTimeDelta);
        updatePhysCenterOfMass();
        calculateFramedMOITensor();

        if (!this.parent.isShipAligningToGrid()) {
            this.applyAirDrag();
            if (!this.actAsArchimedes) calculateForces();
        }
        else this.calculateForcesDeconstruction(physTickTimeDelta);
    }

    @Deprecated
    public void rawPhysTickPreCol(double physTickTimeDelta) {
        prePhysXTick(physTickTimeDelta);
    }

    @Deprecated
    public void rawPhysTickPostCol() {
        finishPhysXTick(new ShipTransform(physX, physY, physZ, physRotation, physCenterOfMass));
    }

    public void drainImpulseTo(PhysXShipBody shipBody) {
        shipBody.addImpulse(new Vector3d(force), new Vector3d(torque));
        force.zero();
        torque.zero();
    }

    public void finishPhysXTick(ShipTransform finalPhysTransform) {
        physX = finalPhysTransform.getPosX();
        physY = finalPhysTransform.getPosY();
        physZ = finalPhysTransform.getPosZ();
        physRotation = finalPhysTransform.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL);
        physCenterOfMass = finalPhysTransform.getCenterCoord();

        if (isPhysicsBroken()) {
            getParent().getShipData().setPhysicsEnabled(false);
            getLinearVelocity().zero();
            getAngularVelocity().zero();
        }

        getParent().getShipTransformationManager().updatePreviousPhysicsTransform();
        getParent().getShipTransformationManager().setCurrentPhysicsTransform(finalPhysTransform);
        getParent().getShipData().getPhysicsData().setAngularVelocity(new Vector3d(angularVelocity));
        getParent().getShipData().getPhysicsData().setLinearVelocity(new Vector3d(linearVelocity));
    }

    public boolean consumeForceToUseGameTransform() {
        boolean result = forceToUseGameTransform;
        forceToUseGameTransform = false;
        return result;
    }

    private boolean isPhysicsBroken() {
        if (this.getAngularVelocity().lengthSquared() > 50000
                || this.getLinearVelocity().lengthSquared() > 50000
                || !this.getAngularVelocity().isFinite()
                || !this.getLinearVelocity().isFinite()
        ) {
            System.out.println("Ship tried moving too fast; freezing it and resetting velocities");
            return true;
        }
        return false;
    }

    private void updatePhysCenterOfMass() {
        Vector3dc gameTickCM = this.parent.getInertiaData().getGameTickCenterOfMass();
        if (!this.physCenterOfMass.equals(gameTickCM)) {
            Vector3d centerDifference = gameTickCM.sub(this.physCenterOfMass, new Vector3d());
            getParent().getShipTransformationManager().getCurrentPhysicsTransform()
                    .transformDirection(centerDifference, TransformType.SUBSPACE_TO_GLOBAL);
            this.physX += centerDifference.x;
            this.physY += centerDifference.y;
            this.physZ += centerDifference.z;
            this.physCenterOfMass = gameTickCM;
            this.centerOfMassPoseDirty = true;
            getParent().getShipData().getPhysicsData().markCollisionShapeDirty();
        }
    }

    public ShipTransform createPhysTransform() {
        return new ShipTransform(this.physX, this.physY, this.physZ, this.physRotation, this.physCenterOfMass);
    }

    public boolean consumeCenterOfMassPoseDirty() {
        boolean result = this.centerOfMassPoseDirty;
        this.centerOfMassPoseDirty = false;
        return result;
    }

    private void calculateFramedMOITensor() {
        this.physTickMass = Math.max(this.parent.getInertiaData().getGameTickMass(), 0.0001D);
        Matrix3dc rotationMatrix = this.getParent().getShipTransformationManager()
                .getCurrentPhysicsTransform().createRotationMatrix(TransformType.SUBSPACE_TO_GLOBAL);
        Matrix3dc inertiaBodyFrame = this.parent.getInertiaData().getGameMoITensor();
        Matrix3d rotationMatrixTranspose = rotationMatrix.transpose(new Matrix3d());
        Matrix3d finalInertia = new Matrix3d(rotationMatrix);
        finalInertia.mul(inertiaBodyFrame);
        finalInertia.mul(rotationMatrixTranspose);
        this.physMOITensor = finalInertia;
        this.physInvMOITensor = this.physMOITensor.invert(new Matrix3d());
    }

    private void calculateForces() {
        Vector3d blockForce = new Vector3d();
        Vector3d inBodyWO = new Vector3d();
        Vector3d crossVector = new Vector3d();
        World worldObj = getParent().getWorld();

        if (VSConfig.doPhysicsBlocks) {
            Queue<IPhysicsBlockController> nodesPriorityQueue = new PriorityQueue<>(this.parent.getPhysicsControllersInShip());
            while (!nodesPriorityQueue.isEmpty()) {
                IPhysicsBlockController controller = nodesPriorityQueue.poll();
                controller.onPhysicsTick(this.parent, this, this.getPhysicsTimeDeltaPerPhysTick());
            }

            SortedMap<IBlockTorqueProvider, List<BlockPos>> torqueProviders = new TreeMap<>();
            BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
            for (BlockPos activeForcePos : this.parent.getShipData().activeForcePositions) {
                mutablePos.setPos(activeForcePos);
                IBlockState state = getParent().getChunkAt(mutablePos.getX() >> 4, mutablePos.getZ() >> 4).getBlockState(mutablePos);
                Block blockAt = state.getBlock();

                if (blockAt instanceof IBlockForceProvider blockForceProvider) {
                    try {
                        BlockPhysicsDetails.getForceFromState(
                                state, mutablePos, worldObj,
                                getPhysicsTimeDeltaPerPhysTick(), this.getParent(), blockForce
                        );
                        Vector3dc otherPosition = blockForceProvider.getCustomBlockForcePosition(
                                worldObj, mutablePos, state,
                                this.getParent(), getPhysicsTimeDeltaPerPhysTick()
                        );

                        if (otherPosition != null) inBodyWO.set(otherPosition);
                        else inBodyWO.set(mutablePos.getX() + 0.5, mutablePos.getY() + 0.5, mutablePos.getZ() + 0.5);

                        inBodyWO.sub(this.physCenterOfMass);
                        this.getParent().getShipTransformationManager()
                                .getCurrentPhysicsTransform()
                                .transformDirection(inBodyWO, TransformType.SUBSPACE_TO_GLOBAL);
                        this.addForceAtPoint(inBodyWO, blockForce, crossVector);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                if (blockAt instanceof IBlockTorqueProvider torqueProviderBlock) {
                    torqueProviders.computeIfAbsent(torqueProviderBlock, ignored -> new LinkedList<>()).add(new BlockPos(activeForcePos));
                }
            }

            for (IBlockTorqueProvider torqueProviderBlock : torqueProviders.keySet()) {
                List<BlockPos> blockPositions = torqueProviders.get(torqueProviderBlock);
                for (BlockPos pos : blockPositions) {
                    Vector3dc torqueVector = torqueProviderBlock.getTorqueInGlobal(this, pos);
                    if (torqueVector != null) this.torque.add(torqueVector);
                }
            }
        }

        final ShipPilot parentPilot = this.parent.getShipPilot();
        if (parentPilot != null) {
            final Vector3dc pilotForce = parentPilot.getBlockForceInShipSpace(this.parent, this.physTickTimeDelta);
            final Vector3dc pilotTorque = parentPilot.getTorqueInGlobal(this);
            if (pilotForce != null) this.force.add(pilotForce);
            if (pilotTorque != null) this.torque.add(pilotTorque);
        }
    }

    private void calculateForcesDeconstruction(double physTickTimeDelta) {
        this.applyAirDrag();
        Quaterniondc inverseCurrentRotation = this.parent.getShipTransformationManager()
                .getCurrentPhysicsTransform().rotationQuaternion(TransformType.GLOBAL_TO_SUBSPACE);
        AxisAngle4d idealAxisAngle = new AxisAngle4d(inverseCurrentRotation);
        if (idealAxisAngle.angle < EPSILON) return;
        idealAxisAngle.normalize();
        double angleBetweenIdealAndActual = idealAxisAngle.angle;
        if (angleBetweenIdealAndActual > Math.PI) {
            angleBetweenIdealAndActual = 2 * Math.PI - angleBetweenIdealAndActual;
        }
        double idealAngularVelocityMultiple = angleBetweenIdealAndActual;
        Vector3d idealAngularVelocity = new Vector3d(idealAxisAngle.x, idealAxisAngle.y, idealAxisAngle.z);
        idealAngularVelocity.mul(idealAngularVelocityMultiple);
        Vector3d angularVelocityDifference = idealAngularVelocity.sub(this.getAngularVelocity(), new Vector3d());
        angularVelocityDifference.mul(physTickTimeDelta);
        this.getAngularVelocity().add(angularVelocityDifference);
    }

    private void applyAirDrag() {
        double drag = this.getDragForPhysTick();
        this.getLinearVelocity().mul(drag);
        this.getAngularVelocity().mul(drag);
    }

    @Deprecated
    public void addForceAtPoint(Vector3dc inBodyWO, Vector3dc forceToApply) {
        addForceAtPoint(inBodyWO, forceToApply, new Vector3d());
    }

    @Deprecated
    public void addForceAtPoint(Vector3dc inBodyWO, Vector3dc forceToApply, Vector3d crossVector) {
        inBodyWO.cross(forceToApply, crossVector);
        torque.add(crossVector);
        force.add(forceToApply);
    }

    public void addForceAtPointNew(Vector3dc posRelToShipCenter, Vector3dc forceToApply, Vector3d tempStorage) {
        final double timeStep = getPhysicsTimeDeltaPerPhysTick();
        posRelToShipCenter.cross(forceToApply, tempStorage);
        torque.add(tempStorage.x() * timeStep, tempStorage.y() * timeStep, tempStorage.z() * timeStep);
        force.add(forceToApply.x() * timeStep, forceToApply.y() * timeStep, forceToApply.z() * timeStep);
    }

    private void updatePhysSpeedAndIters(double newPhysSpeed) {
        physTickTimeDelta = newPhysSpeed;
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

    public double getMass() {
        return physTickMass;
    }

    public double getInvMass() {
        return 1.0 / physTickMass;
    }

    public double getPhysicsTimeDeltaPerPhysTick() {
        return physTickTimeDelta;
    }

    public double getDragForPhysTick() {
        return Math.pow(DRAG_CONSTANT, getPhysicsTimeDeltaPerPhysTick() * 20D);
    }

    public Matrix3dc getPhysInvMOITensor() {
        return physInvMOITensor;
    }

    public Matrix3dc getPhysMOITensor() {
        return this.physMOITensor;
    }

    public PhysicsObject getParent() {
        return parent;
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

    public Vector3dc getPhysCenterOfMass() {
        return this.physCenterOfMass;
    }

    public Vector3d getLinearVelocity() {
        return this.linearVelocity;
    }

    public Vector3d getAngularVelocity() {
        return this.angularVelocity;
    }

    public void setLinearVelocity(Vector3dc linearVelocity) {
        this.linearVelocity.set(linearVelocity);
    }

    public void setAngularVelocity(Vector3dc angularVelocity) {
        this.angularVelocity.set(angularVelocity);
    }

    public void setForceToUseGameTransform(boolean forceToUseGameTransform) {
        this.forceToUseGameTransform = forceToUseGameTransform;
    }
}
