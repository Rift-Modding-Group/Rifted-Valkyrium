package org.valkyrienskies.addon.control.tileentity;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.addon.control.config.VSControlConfig;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;
import valkyrienwarfare.api.TransformType;

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

        Vector3d torqueImpulse = physicsCalculations.getPhysMOITensor().transform(angularVelocityChange, new Vector3d());
        torqueImpulse.mul(physicsCalculations.getPhysicsTimeDeltaPerPhysTick());

        //application of impulse that keeps the ship leveled happens here
        double maxTorqueImpulse = VSControlConfig.stabilizerMaxTorque * physicsCalculations.getPhysicsTimeDeltaPerPhysTick();
        double torqueImpulseMagnitude = torqueImpulse.length();
        if (torqueImpulseMagnitude > maxTorqueImpulse) {
            torqueImpulse.mul(maxTorqueImpulse / torqueImpulseMagnitude);
        }
        return torqueImpulse;
    }

}
