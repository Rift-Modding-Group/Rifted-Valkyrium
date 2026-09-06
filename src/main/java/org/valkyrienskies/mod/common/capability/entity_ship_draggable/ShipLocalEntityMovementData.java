package org.valkyrienskies.mod.common.capability.entity_ship_draggable;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.api.TransformType;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;

import java.util.UUID;

/**
 * Client-side interpolation state for a server-controlled entity moving relative to a ship.
 */
public class ShipLocalEntityMovementData {
    public static final int DEFAULT_LERP_STEPS = 3;

    @Nullable
    private UUID shipUuid;
    private boolean initialized;
    private boolean hasPositionTarget;
    private boolean hasVelocity;
    private boolean hasRotationTarget;
    private boolean hasHeadRotationTarget;
    private boolean onGround;
    @NotNull
    private final Vector3d previousRelativePosition = new Vector3d();
    @NotNull
    private final Vector3d relativePosition = new Vector3d();
    @NotNull
    private final Vector3d targetRelativePosition = new Vector3d();
    @NotNull
    private final Vector3d relativeVelocity = new Vector3d();
    private double previousRelativeYaw;
    private double relativeYaw;
    private double targetRelativeYaw;
    private double previousRelativeHeadYaw;
    private double relativeHeadYaw;
    private double targetRelativeHeadYaw;
    private double previousPitch;
    private double pitch;
    private double targetPitch;
    private int positionLerpSteps;
    private int rotationLerpSteps;
    private int headRotationLerpSteps;

    public boolean isActive() {
        return this.shipUuid != null;
    }

    public boolean isInitialized() {
        return this.initialized;
    }

    @Nullable
    public UUID getShipUuid() {
        return this.shipUuid;
    }

    public void useShip(@NotNull UUID newShipUuid) {
        if (newShipUuid.equals(this.shipUuid)) return;

        this.shipUuid = newShipUuid;
        this.initialized = false;
        this.hasPositionTarget = false;
        this.hasVelocity = false;
        this.hasRotationTarget = false;
        this.hasHeadRotationTarget = false;
        this.positionLerpSteps = 0;
        this.rotationLerpSteps = 0;
        this.headRotationLerpSteps = 0;
    }

    public void initializeIfNeeded(
            @NotNull ShipTransform shipTransform,
            @NotNull Vector3dc worldPosition,
            double worldYaw,
            double worldPitch,
            double worldHeadYaw
    ) {
        if (this.initialized) return;

        this.relativePosition.set(worldPosition);
        shipTransform.transformPosition(this.relativePosition, TransformType.GLOBAL_TO_SUBSPACE);
        this.previousRelativePosition.set(this.relativePosition);
        if (!this.hasPositionTarget) {
            this.targetRelativePosition.set(this.relativePosition);
        }

        this.relativeYaw = transformYaw(shipTransform, worldYaw, TransformType.GLOBAL_TO_SUBSPACE);
        this.previousRelativeYaw = this.relativeYaw;
        if (!this.hasRotationTarget) {
            this.targetRelativeYaw = this.relativeYaw;
            this.targetPitch = worldPitch;
        }
        this.pitch = worldPitch;
        this.previousPitch = worldPitch;

        this.relativeHeadYaw = transformYaw(shipTransform, worldHeadYaw, TransformType.GLOBAL_TO_SUBSPACE);
        this.previousRelativeHeadYaw = this.relativeHeadYaw;
        if (!this.hasHeadRotationTarget) {
            this.targetRelativeHeadYaw = this.relativeHeadYaw;
        }
        this.initialized = true;
    }

    public void setPositionTarget(@NotNull Vector3dc targetPosition, int lerpSteps) {
        this.targetRelativePosition.set(targetPosition);
        this.hasPositionTarget = true;
        this.positionLerpSteps = Math.max(lerpSteps, 0);
        if (this.initialized && this.positionLerpSteps == 0) {
            this.relativePosition.set(this.targetRelativePosition);
            this.previousRelativePosition.set(this.relativePosition);
        }
    }

    public void setVelocity(@NotNull Vector3dc velocity) {
        this.relativeVelocity.set(velocity);
        this.hasVelocity = true;
    }

    public void setRotationTarget(double targetYaw, double newTargetPitch, int lerpSteps) {
        this.targetRelativeYaw = targetYaw;
        this.targetPitch = newTargetPitch;
        this.hasRotationTarget = true;
        this.rotationLerpSteps = Math.max(lerpSteps, 0);
        if (this.initialized && this.rotationLerpSteps == 0) {
            this.relativeYaw = targetYaw;
            this.previousRelativeYaw = targetYaw;
            this.pitch = newTargetPitch;
            this.previousPitch = newTargetPitch;
        }
    }

    public void setHeadRotationTarget(double targetHeadYaw, int lerpSteps) {
        this.targetRelativeHeadYaw = targetHeadYaw;
        this.hasHeadRotationTarget = true;
        this.headRotationLerpSteps = Math.max(lerpSteps, 0);
        if (this.initialized && this.headRotationLerpSteps == 0) {
            this.relativeHeadYaw = targetHeadYaw;
            this.previousRelativeHeadYaw = targetHeadYaw;
        }
    }

    public void setOnGround(boolean newOnGround) {
        this.onGround = newOnGround;
    }

    public void advance() {
        if (!this.initialized) return;

        this.previousRelativePosition.set(this.relativePosition);
        this.previousRelativeYaw = this.relativeYaw;
        this.previousRelativeHeadYaw = this.relativeHeadYaw;
        this.previousPitch = this.pitch;

        if (this.positionLerpSteps > 0) {
            double inverseSteps = 1D / this.positionLerpSteps;
            this.relativePosition.lerp(this.targetRelativePosition, inverseSteps);
            this.positionLerpSteps--;
        }
        if (this.rotationLerpSteps > 0) {
            this.relativeYaw += wrapDegrees(this.targetRelativeYaw - this.relativeYaw) / this.rotationLerpSteps;
            this.pitch += (this.targetPitch - this.pitch) / this.rotationLerpSteps;
            this.rotationLerpSteps--;
        }
        if (this.headRotationLerpSteps > 0) {
            this.relativeHeadYaw += wrapDegrees(this.targetRelativeHeadYaw - this.relativeHeadYaw) / this.headRotationLerpSteps;
            this.headRotationLerpSteps--;
        }
    }

    @NotNull
    public Vector3d getWorldPosition(@NotNull ShipTransform shipTransform, double partialTicks) {
        double clampedPartialTicks = Math.clamp(partialTicks, 0D, 1D);
        Vector3d worldPosition = this.previousRelativePosition.lerp(this.relativePosition, clampedPartialTicks, new Vector3d());
        shipTransform.transformPosition(worldPosition, TransformType.SUBSPACE_TO_GLOBAL);
        return worldPosition;
    }

    @NotNull
    public Vector3d getWorldVelocity(@NotNull ShipTransform shipTransform) {
        Vector3d worldVelocity = new Vector3d(this.relativeVelocity);
        shipTransform.transformDirection(worldVelocity, TransformType.SUBSPACE_TO_GLOBAL);
        return worldVelocity;
    }

    public double getWorldYaw(@NotNull ShipTransform shipTransform, double partialTicks) {
        double clampedPartialTicks = Math.clamp(partialTicks, 0D, 1D);
        double interpolatedYaw = this.previousRelativeYaw + wrapDegrees(this.relativeYaw - this.previousRelativeYaw) * clampedPartialTicks;
        return transformYaw(shipTransform, interpolatedYaw, TransformType.SUBSPACE_TO_GLOBAL);
    }

    public double getWorldHeadYaw(@NotNull ShipTransform shipTransform, double partialTicks) {
        double clampedPartialTicks = Math.clamp(partialTicks, 0D, 1D);
        double interpolatedYaw = this.previousRelativeHeadYaw + wrapDegrees(this.relativeHeadYaw - this.previousRelativeHeadYaw) * clampedPartialTicks;
        return transformYaw(shipTransform, interpolatedYaw, TransformType.SUBSPACE_TO_GLOBAL);
    }

    public double getPitch(double partialTicks) {
        double clampedPartialTicks = Math.clamp(partialTicks, 0D, 1D);
        return this.previousPitch + (this.pitch - this.previousPitch) * clampedPartialTicks;
    }

    public boolean hasVelocity() {
        return this.hasVelocity;
    }

    public boolean isOnGround() {
        return this.onGround;
    }

    @NotNull
    public Vector3d getRelativeMovement() {
        return this.relativePosition.sub(this.previousRelativePosition, new Vector3d());
    }

    public void clear() {
        this.shipUuid = null;
        this.initialized = false;
        this.hasPositionTarget = false;
        this.hasVelocity = false;
        this.hasRotationTarget = false;
        this.hasHeadRotationTarget = false;
        this.positionLerpSteps = 0;
        this.rotationLerpSteps = 0;
        this.headRotationLerpSteps = 0;
    }

    public static double transformYaw(@NotNull ShipTransform shipTransform, double yaw, @NotNull TransformType transformType) {
        Vector3d shipForward = new Vector3d(0D, 0D, 1D);
        shipTransform.transformDirection(shipForward, TransformType.SUBSPACE_TO_GLOBAL);
        double shipYaw = Math.toDegrees(Math.atan2(-shipForward.x, shipForward.z));
        if (transformType == TransformType.GLOBAL_TO_SUBSPACE) return wrapDegrees(yaw - shipYaw);
        return wrapDegrees(yaw + shipYaw);
    }

    private static double wrapDegrees(double degrees) {
        double wrappedDegrees = degrees % 360D;
        if (wrappedDegrees >= 180D) wrappedDegrees -= 360D;
        if (wrappedDegrees < -180D) wrappedDegrees += 360D;
        return wrappedDegrees;
    }
}
