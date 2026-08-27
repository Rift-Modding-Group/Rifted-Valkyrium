package org.valkyrienskies.addon.control.tileentity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.addon.control.config.VSControlConfig;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;
import org.valkyrienskies.api.TransformType;

public class TileEntityGyroscopeStabilizer extends TileEntity {
    // The direction we are want to align to.
    private static final Vector3dc GRAVITY_UP = new Vector3d(0, 1, 0);
    private static final double MIN_ALIGNMENT_ERROR = 1.0E-6D;
    private static final double LEVELING_RESPONSE = 3.0D;

    public Vector3dc getTorqueInGlobal(PhysicsCalculations physicsCalculations, BlockPos pos) {
        Vector3d shipLevelNormal = new Vector3d(GRAVITY_UP);
        physicsCalculations.getParent().getShipTransformationManager().getCurrentPhysicsTransform()
            .transformDirection(shipLevelNormal, TransformType.SUBSPACE_TO_GLOBAL);
        shipLevelNormal.normalize();

        Vector3d correctionAxis = shipLevelNormal.cross(GRAVITY_UP, new Vector3d());
        if (correctionAxis.lengthSquared() < MIN_ALIGNMENT_ERROR) return new Vector3d();
        correctionAxis.normalize();

        double angleBetween = GRAVITY_UP.angle(shipLevelNormal);
        Vector3d targetAngularVelocity = correctionAxis.mul(angleBetween * LEVELING_RESPONSE, new Vector3d());
        Vector3d currentCorrectionVelocity = correctionAxis.mul(
            physicsCalculations.getAngularVelocity().dot(correctionAxis),
            new Vector3d()
        );
        Vector3d angularVelocityChange = targetAngularVelocity.sub(currentCorrectionVelocity, new Vector3d());

        Vector3d torque = physicsCalculations.getPhysMOITensor().transform(angularVelocityChange, new Vector3d());
        torque.sub(new Vector3d(GRAVITY_UP).mul(torque.dot(GRAVITY_UP)));

        double torqueMagnitude = torque.length();
        if (torqueMagnitude > VSControlConfig.stabilizerMaxTorque) {
            torque.mul(VSControlConfig.stabilizerMaxTorque / torqueMagnitude);
        }
        return torque;
    }

}
