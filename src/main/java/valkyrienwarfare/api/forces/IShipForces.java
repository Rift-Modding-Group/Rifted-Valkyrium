package valkyrienwarfare.api.forces;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3dc;
import valkyrienwarfare.api.IPhysicsEntity;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * A ship's force accumulator for the current physics step.
 * <p>
 * Force values are measured in newtons and torque values are measured in newton-metres. Methods
 * whose names contain {@code Ship} accept directions or positions in the ship's block coordinate
 * system. World-space values do not rotate with the ship.
 */
@ParametersAreNonnullByDefault
public interface IShipForces {
    /**
     * @return the ship receiving these forces
     */
    @NotNull
    IPhysicsEntity getShip();

    /**
     * @return the force acting on the ship
     * */
    @NotNull
    Vector3dc getWorldForce();

    /**
     * @return the torque acting on the ship
     * */
    @NotNull
    Vector3dc getWorldTorque();

    /**
     * @return the duration of the current physics step in seconds
     */
    double getTimeStepSeconds();

    /**
     * Applies a world-space force through the ship's center of mass.
     *
     * @param force force in newtons
     */
    void applyWorldForce(Vec3d force);

    /**
     * Applies a world-space torque.
     *
     * @param torque torque in newton-metres
     */
    void applyWorldTorque(Vec3d torque);

    /**
     * Applies a world-space force at an absolute world-space position.
     *
     * @param force force in newtons
     * @param worldPosition position at which the force acts
     */
    void applyWorldForceAtWorldPosition(Vec3d force, Vec3d worldPosition);

    /**
     * Applies a force whose direction rotates with the ship through its center of mass.
     *
     * @param force force in ship space, in newtons
     */
    void applyShipForce(Vec3d force);

    /**
     * Applies a torque whose direction rotates with the ship.
     *
     * @param torque torque in ship space, in newton-metres
     */
    void applyShipTorque(Vec3d torque);

    /**
     * Applies a world-space force at a position expressed in the ship's block coordinates.
     * This is useful for balloons and other devices that always push toward world-space up.
     *
     * @param force force in newtons
     * @param shipPosition position in the ship's block coordinate system
     */
    void applyWorldForceAtShipPosition(Vec3d force, Vec3d shipPosition);

    /**
     * Applies a force and position that both rotate with the ship.
     * This is useful for engines and thrusters.
     *
     * @param force force in ship space, in newtons
     * @param shipPosition position in the ship's block coordinate system
     */
    void applyShipForceAtShipPosition(Vec3d force, Vec3d shipPosition);

    /**
     * Applies upward buoyancy through the ship's center of mass.
     *
     * @param force upward force in newtons
     */
    default void applyBuoyancy(double force) {
        this.applyWorldForce(new Vec3d(0D, force, 0D));
    }

    /**
     * Applies upward buoyancy at a position in the ship's block coordinates.
     *
     * @param force upward force in newtons
     * @param shipPosition position in the ship's block coordinate system
     */
    default void applyBuoyancy(double force, Vec3d shipPosition) {
        this.applyWorldForceAtShipPosition(new Vec3d(0D, force, 0D), shipPosition);
    }
}
