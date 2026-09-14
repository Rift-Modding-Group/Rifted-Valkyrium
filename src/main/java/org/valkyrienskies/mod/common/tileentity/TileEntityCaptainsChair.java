package org.valkyrienskies.mod.common.tileentity;

import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.BlockPos;
import org.joml.AxisAngle4d;
import org.joml.Matrix3d;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.block.BlockCaptainsChair;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;
import org.valkyrienskies.mod.common.piloting.PilotControls;
import org.valkyrienskies.mod.common.piloting.PilotControlsMessage;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import valkyrienwarfare.api.TransformType;

public class TileEntityCaptainsChair extends TileEntityPilotableImpl implements ITickable {
    @Override
    public final void onStartTileUsage() {
        this.applyChairStabilization(false);
    }

    @Override
    public final void onStopTileUsage() {
        this.applyChairStabilization(true);

        super.onStopTileUsage();
    }

    @Override
    public void update() {
        if (this.getWorld().isRemote) return;
        this.applyChairStabilization(this.getPilotEntity() == null);
    }

    /**
     * This ensures that the ship this chair is attached to maintains its y position when in the air.
     * Must be ticked while the chair is on a ship.
     * */
    private void applyChairStabilization(boolean holdYDisplacement) {
        PhysicsObject physicsObject = this.getParentPhysicsEntity();
        if (physicsObject != null) {
            PhysicsCalculations physicsCalculations = physicsObject.getPhysicsCalculations();
            physicsCalculations.actAsArchimedes = true;

            //apply angular stabilization
            Vector3d shipUp = new Vector3d(0, 1, 0);
            physicsObject.getShipTransformationManager()
                    .getCurrentPhysicsTransform()
                    .transformDirection(shipUp, TransformType.SUBSPACE_TO_GLOBAL);

            Vector3d idealUp = new Vector3d(0, 1, 0);
            Vector3d targetAngularVelocity = new Vector3d();
            double angleBetween = shipUp.angle(idealUp);
            if (angleBetween > 0.01D) {
                Vector3d correctionAxis = shipUp.cross(idealUp, new Vector3d());
                if (correctionAxis.lengthSquared() > PhysicsCalculations.EPSILON) {
                    correctionAxis.normalize();
                    targetAngularVelocity.set(correctionAxis).mul(angleBetween * 2D);
                }
            }

            Vector3d angularVelocity = physicsCalculations.getAngularVelocity();
            Vector3d stabilizationDifference = new Vector3d(
                    angularVelocity.x - targetAngularVelocity.x,
                    0,
                    angularVelocity.z - targetAngularVelocity.z
            );
            stabilizationDifference.mul(0.2D);
            angularVelocity.sub(stabilizationDifference);

            //hold y displacement (aka block ship from falling)
            if (holdYDisplacement) physicsCalculations.getLinearVelocity().y = 0;
        }
    }

    @Override
    public void onBlockBroken() {
        super.onBlockBroken();

        //revert to normal ship behavior
        PhysicsObject physicsObject = this.getParentPhysicsEntity();
        if (physicsObject != null && !this.hasAnotherCaptainsChair(physicsObject)) {
            physicsObject.getPhysicsCalculations().actAsArchimedes = false;
        }
    }

    //make sure theres no other captains chairs
    private boolean hasAnotherCaptainsChair(PhysicsObject physicsObject) {
        for (BlockPos blockPos : physicsObject.getBlockPositions()) {
            if (blockPos.equals(this.getPos())) continue;
            IBlockState blockState = physicsObject.getWorld().getBlockState(blockPos);
            if (blockState.getBlock() == ValkyrienSkiesMod.INSTANCE.captainsChair) {
                return true;
            }
        }
        return false;
    }

    @Override
    public void processControlMessage(PilotControlsMessage message, EntityPlayerMP sender) {
        IBlockState blockState = this.getWorld().getBlockState(getPos());
        if (blockState.getBlock() != ValkyrienSkiesMod.INSTANCE.captainsChair) {
            this.setPilotEntity(null);
            return;
        }

        PhysicsObject physicsObject = this.getParentPhysicsEntity();
        if (physicsObject == null) return;
        if (physicsObject.isShipAligningToGrid()) return;

        BlockPos chairPosition = this.getPos();

        double pilotPitch = 0D;
        double pilotYaw = ((BlockCaptainsChair) blockState.getBlock()).getChairYaw(blockState, chairPosition);
        double pilotRoll = 0D;

        Matrix3d pilotRotationMatrix = new Matrix3d();

        pilotRotationMatrix.rotateXYZ(Math.toRadians(pilotPitch), Math.toRadians(pilotYaw), Math.toRadians(pilotRoll));

        Vector3d playerDirection = new Vector3d(1, 0, 0);

        pilotRotationMatrix.transform(playerDirection);

        Vector3d upDirection = new Vector3d(0, 1, 0);

        Vector3d downDirection = new Vector3d(0, -1, 0);

        Vector3d idealAngularDirection = new Vector3d();

        Vector3d idealLinearVelocity = new Vector3d();

        Vector3d shipUp = new Vector3d(0, 1, 0);
        Vector3d shipUpPosIdeal = new Vector3d(0, 1, 0);

        if (PilotControls.controlIsPressed(message.getUsedControls(), PilotControls.FORWARD)) {
            idealLinearVelocity.add(playerDirection);
        }
        if (PilotControls.controlIsPressed(message.getUsedControls(), PilotControls.BACKWARD)) {
            idealLinearVelocity.sub(playerDirection);
        }

        physicsObject.getShipTransformationManager().getCurrentTickTransform()
                .transformDirection(idealLinearVelocity, TransformType.SUBSPACE_TO_GLOBAL);
        physicsObject.getShipTransformationManager().getCurrentTickTransform()
                .transformDirection(shipUp, TransformType.SUBSPACE_TO_GLOBAL);

        if (PilotControls.controlIsPressed(message.getUsedControls(), PilotControls.UP)) {
            idealLinearVelocity.add(upDirection.mul(0.5, new Vector3d()));
        }
        if (PilotControls.controlIsPressed(message.getUsedControls(), PilotControls.DOWN)) {
            idealLinearVelocity.add(downDirection.mul(0.5, new Vector3d()));
        }

        double sidePitch = 0;

        if (PilotControls.controlIsPressed(message.getUsedControls(), PilotControls.RIGHT)) {
            idealAngularDirection.sub(shipUp);
            sidePitch -= 10;
        }
        if (PilotControls.controlIsPressed(message.getUsedControls(), PilotControls.LEFT)) {
            idealAngularDirection.add(shipUp);
            sidePitch += 10;
        }

        Vector3d sidesRotationAxis = new Vector3d(playerDirection);
        physicsObject.getShipTransformationManager().getCurrentTickTransform()
                .transformDirection(sidesRotationAxis, TransformType.SUBSPACE_TO_GLOBAL);

        AxisAngle4d rotationSidesTransform = new AxisAngle4d(Math.toRadians(sidePitch), sidesRotationAxis.x, sidesRotationAxis.y,
                sidesRotationAxis.z);

        rotationSidesTransform.transform(shipUpPosIdeal);

        idealAngularDirection.mul(2);
        // The vector that points in the direction of the normal of the plane that
        // contains shipUp and shipUpPos. This is our axis of rotation.
        Vector3d shipUpRotationVector = shipUp.cross(shipUpPosIdeal, new Vector3d());
        // This isnt quite right, but it handles the cases quite well.
        double shipUpTheta = shipUp.angle(shipUpPosIdeal) + Math.PI;
        shipUpRotationVector.mul(shipUpTheta);

        idealAngularDirection.add(shipUpRotationVector);
        idealLinearVelocity.mul(20);

        // Move the ship faster if the player holds the sprint key.
        if (PilotControls.controlIsPressed(message.getUsedControls(), PilotControls.SPRINT)) {
            idealLinearVelocity.mul(2);
        }

        double lerpFactor = 0.2;
        Vector3d linearMomentumDif = physicsObject.getPhysicsCalculations().getLinearVelocity().sub(idealLinearVelocity, new Vector3d());

        Vector3d angularVelocityDif = physicsObject.getPhysicsCalculations().getAngularVelocity().sub(idealAngularDirection, new Vector3d());

        linearMomentumDif.mul(lerpFactor);
        angularVelocityDif.mul(lerpFactor);

        physicsObject.getPhysicsCalculations().getLinearVelocity().sub(linearMomentumDif);
        physicsObject.getPhysicsCalculations().getAngularVelocity().sub(angularVelocityDif);
    }
}
