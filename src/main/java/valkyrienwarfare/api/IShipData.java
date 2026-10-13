package valkyrienwarfare.api;

import net.minecraft.util.math.AxisAlignedBB;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3dc;

import java.util.UUID;

/**
 * Interface representation of ShipData in the API.
 * */
public interface IShipData {
    /**
     * An immutable representation of the linear velocity that a {@link IPhysicsEntity} object travels at
     *
     */
    @NotNull Vector3dc getLinearVelocity();

    /**
     * An immutable representation of the angular velocity that a {@link IPhysicsEntity} object rotates at
     * */
    @NotNull Vector3dc getAngularVelocity();

    /**
     * An immutable representation of the center of mass of a {@link IPhysicsEntity} at the current tick it's called at
     * */
    @NotNull Vector3dc getCenterOfMass();

    /**
     * An immutable representation of the mass in kg of a {@link IPhysicsEntity} at the current tick it's called at
     * */
    double getMass();

    /**
     * The ship's bounding box as an {@link AxisAlignedBB}. Returns an empty {@link AxisAlignedBB} if none exists.
     * */
    @NotNull AxisAlignedBB getShipBB();

    /**
     * @return the {@link UUID} of the {@link IPhysicsEntity}, or a nil value if none exists.
     * */
    @NotNull UUID getUuid();

    /**
     * @return the name of the {@link IPhysicsEntity}, or blank if none exists.
     * */
    @NotNull String getName();

    /**
     * Enable or disable physics on the {@link IPhysicsEntity}
     * */
    void setPhysicsEnabled(boolean value);

    /**
     * @return If physics is enabled on the {@link IPhysicsEntity}
     * */
    boolean isPhysicsEnabled();
}
