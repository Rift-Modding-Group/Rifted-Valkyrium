package org.valkyrienskies.mod.common.ships.ship_world;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaterniondc;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.item.ItemPhysicsStick;
import org.valkyrienskies.mod.common.physics.PhysicsCalculations;
import valkyrienwarfare.api.IShipForces;
import valkyrienwarfare.api.IShipForcesInducer;
import valkyrienwarfare.api.TransformType;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.UUID;

/**
 * Holds a ship using a target published by the server's game thread.
 * */
@ParametersAreNonnullByDefault
public class PhysicsStickController implements IShipForcesInducer {
    public ItemStack stack;
    private final UUID playerId;
    private final Vec3d grabPoint;
    private final Quaterniondc rotation;
    private final float grabYaw;
    private final boolean physicsWasEnabled;
    private final double holdDistance;
    @Nullable
    private volatile HoldTarget target;

    public PhysicsStickController(
            UUID playerId, ItemStack stack, Vec3d grabPoint, Quaterniondc rotation,
            Vec3d target, float grabYaw, boolean physicsWasEnabled, double holdDistance
    ) {
        this.playerId = playerId;
        this.stack = stack;
        this.grabPoint = grabPoint;
        this.rotation = new Quaterniond(rotation);
        this.grabYaw = grabYaw;
        this.physicsWasEnabled = physicsWasEnabled;
        this.holdDistance = holdDistance;
        this.target = new HoldTarget(
                target, calculateHoldRotation(rotation, grabYaw, grabYaw), System.nanoTime() + 500_000_000L
        );
    }

    public static void tick(PhysicsObject ship) {
        PhysicsStickController controller = ship.physicsStickController;
        if (controller == null) return;
        EntityPlayer player = ship.getWorld().getPlayerEntityByUUID(controller.playerId);
        if (player == null || !player.isEntityAlive() || player.isSpectator()
                || (player.getHeldItemMainhand() != controller.stack && player.getHeldItemOffhand() != controller.stack)
                || !ship.isPhysicsEnabled() || ship.getShipData().entityAttachment != null || ship.isDeconstructing()
        ) {
            release(ship);
            return;
        }
        Vec3d eyes = player.getPositionEyes(1F);
        Vec3d worldGrabPoint = ship.getShipTransform().transform(controller.grabPoint, TransformType.SUBSPACE_TO_GLOBAL);
        if (!ItemPhysicsStick.isWithinReach(eyes, worldGrabPoint)) {
            release(ship);
            return;
        }
        Quaterniond holdRotation = calculateHoldRotation(controller.rotation, controller.grabYaw, player.rotationYaw);
        controller.target = new HoldTarget(
                eyes.add(player.getLookVec().scale(controller.holdDistance)), holdRotation,
                System.nanoTime() + 500_000_000L
        );
    }

    public static void release(PhysicsObject ship) {
        PhysicsStickController controller = ship.physicsStickController;
        if (controller == null) return;

        controller.target = null;
        ship.removeForceInducer(controller);
        ship.physicsStickController = null;
        if (!controller.physicsWasEnabled) ship.setPhysicsEnabled(false);
        NBTTagCompound tag = controller.stack.getTagCompound();
        if (tag != null) tag.removeTag(ItemPhysicsStick.SELECTION_TAG);
    }

    @Override
    public void applyForces(IShipForces forces) {
        HoldTarget snapshot = this.target;
        if (snapshot == null || System.nanoTime() > snapshot.expiresAt() || !(forces instanceof PhysicsCalculations calculations)) return;
        PhysicsObject ship = calculations.getParent();
        Vec3d currentGrabPoint = ship.transformVector(this.grabPoint, TransformType.SUBSPACE_TO_GLOBAL);
        Vector3d velocity = calculations.getLinearVelocity();
        Vec3d gravity = Vec3d.ZERO;
        if (VSConfig.doGravity && !calculations.actAsArchimedes && !ship.isShipAligningToGrid()) {
            gravity = new Vec3d(VSConfig.gravityVecX, VSConfig.gravityVecY, VSConfig.gravityVecZ);
        }
        Vec3d acceleration = calculateHoldAcceleration(
                snapshot.point().subtract(currentGrabPoint), new Vec3d(velocity.x, velocity.y, velocity.z),
                gravity, forces.getTimeStepSeconds()
        );
        double mass = ship.getInertiaData().getGameTickMass();
        forces.applyWorldForce(acceleration.scale(mass));

        Quaterniond difference = new Quaterniond(snapshot.rotation()).mul(
                ship.getShipTransformationManager().getCurrentPhysicsTransform()
                        .rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL).conjugate()
        ).normalize();
        Vector3d angularError = new Vector3d(difference.x, difference.y, difference.z);
        if (difference.w < 0D) angularError.negate();
        double sine = angularError.length();
        if (sine > 1E-8) angularError.mul(2D * Math.atan2(sine, Math.abs(difference.w)) / sine);
        else angularError.zero();
        angularError.mul(6D);
        if (angularError.lengthSquared() > 9D) angularError.normalize(3D);
        Vector3d angularAcceleration = angularError.sub(calculations.getAngularVelocity()).mul(10D);
        if (angularAcceleration.lengthSquared() > 400D) angularAcceleration.normalize(20D);
        Vector3d torque = calculations.getPhysMOITensor().transform(angularAcceleration);
        forces.applyWorldTorque(new Vec3d(torque.x, torque.y, torque.z));
    }

    /**
     * Uses bounded velocity and damping to approach the hold point without teleporting.
     * */
    @NotNull
    public static Vec3d calculateHoldAcceleration(Vec3d displacement, Vec3d velocity, Vec3d gravity, double timeStep) {
        if (timeStep <= 0D) return Vec3d.ZERO;
        Vec3d desiredVelocity = displacement.scale(6D);
        if (desiredVelocity.lengthSquared() > 144D) desiredVelocity = desiredVelocity.normalize().scale(12D);
        Vec3d acceleration = desiredVelocity.subtract(velocity).scale(Math.min(10D, 1D / timeStep));
        if (acceleration.lengthSquared() > 6400D) acceleration = acceleration.normalize().scale(80D);
        return acceleration.subtract(gravity);
    }

    /**
     * Levels the ship while preserving its horizontal heading and following the player's yaw.
     * */
    @NotNull
    public static Quaterniond calculateHoldRotation(Quaterniondc grabbedRotation, float grabYaw, float yaw) {
        Vector3d forward = grabbedRotation.transform(new Vector3d(0D, 0D, 1D));
        double heading;
        if (forward.x * forward.x + forward.z * forward.z > 1E-12) {
            heading = Math.atan2(forward.x, forward.z);
        }
        else {
            Vector3d right = grabbedRotation.transform(new Vector3d(1D, 0D, 0D));
            heading = Math.atan2(-right.z, right.x);
        }
        return new Quaterniond().rotationY(heading - Math.toRadians((double) yaw - grabYaw));
    }

    private record HoldTarget(Vec3d point, Quaterniondc rotation, long expiresAt) {}
}
