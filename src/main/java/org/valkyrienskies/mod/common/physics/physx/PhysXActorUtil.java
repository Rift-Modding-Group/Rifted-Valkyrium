package org.valkyrienskies.mod.common.physics.physx;

import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import physx.common.PxQuat;
import physx.common.PxTransform;
import physx.common.PxVec3;
import valkyrienwarfare.api.TransformType;

/**
 * Helper class to convert joml vectors and quaternions into their counterparts for physX.
 * (now that i think about it why does this mod use joml??? lwjgl should be enough)
 * */
public class PhysXActorUtil {
    public static PxVec3 toPxVec(Vector3dc vector) {
        return new PxVec3((float) vector.x(), (float) vector.y(), (float) vector.z());
    }

    public static PxVec3 toPxVec(double x, double y, double z) {
        return new PxVec3((float) x, (float) y, (float) z);
    }

    public static PxQuat toPxQuat(Quaterniondc quaternion) {
        return new PxQuat((float) quaternion.x(), (float) quaternion.y(), (float) quaternion.z(), (float) quaternion.w());
    }

    public static PxTransform toPxTransform(double x, double y, double z, Quaterniondc quaternion) {
        PxVec3 position = toPxVec(x, y, z);
        PxQuat rotation = toPxQuat(quaternion);
        PxTransform transform = new PxTransform(position, rotation);
        position.destroy();
        rotation.destroy();
        return transform;
    }

    public static PxTransform toPxTransform(ShipTransform transform) {
        return toPxTransform(
            transform.getPosX(),
            transform.getPosY(),
            transform.getPosZ(),
            transform.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL)
        );
    }

    public static Vector3d fromPxVec(PxVec3 vector) {
        return new Vector3d(vector.getX(), vector.getY(), vector.getZ());
    }

    public static Quaterniond fromPxQuat(PxQuat quaternion) {
        return new Quaterniond(quaternion.getX(), quaternion.getY(), quaternion.getZ(), quaternion.getW()).normalize();
    }
}
