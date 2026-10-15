package valkyrienwarfare.api;

import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import valkyrienwarfare.api.forces.IShipForcesInducer;

/**
 * This interface is a boiled down version of PhysicsObject used in Rifted Valkyrium.
 * 
 * @author thebest108
 *
 */
public interface IPhysicsEntity {
	/**
	 * Rotates a vector from global to ship space, or from ship to global space.
	 * Typically used when adjusting the velocity vector of entities.
	 * 
	 * @param vector
	 * @param transformType
	 * @return
	 */
	Vec3d rotateVector(Vec3d vector, TransformType transformType);

	/**
	 * Transforms a vector from global to ship space, or from ship to global space.
	 * Typically used when adjusting the position vector of entities.
	 * 
	 * @param vector
	 * @param transformType
	 * @return
	 */
	Vec3d transformVector(Vec3d vector, TransformType transformType);

	/**
	 * Get the {@link World} that a physics entity is currently in
	 * */
	World getWorld();

	/**
	 * Get the {@link IShipData} of a physics entity
	 * */
	IShipData getShipData();

	/**
	 * Enable or disable aligning to world grid on the {@link IPhysicsEntity}
	 * */
	void setShipAligningToGrid(boolean shipAligningToGrid);

	/**
	 * @return If the {@link IPhysicsEntity} is aligning to the world grid
	 * */
	boolean isShipAligningToGrid();

	/**
	 * Deconstruct the ship back into a structure in the world
	 * */
	void setDeconstruct(boolean value);

	/**
	 * Check if a ship is deconstructing back into a structure in the world
	 * */
	boolean isDeconstructing();

	/**
	 * Registers a callback that can apply forces, torque, and buoyancy on every physics step.
	 * The callback is only invoked for a loaded server-side ship while physics is enabled.
	 *
	 * @param forceInducer callback to register
	 * @return {@code true} when the callback was newly registered
	 */
	default boolean addForceInducer(IShipForcesInducer forceInducer) {
		throw new UnsupportedOperationException("This physics entity does not support force inducers");
	}

	/**
	 * Stops invoking a previously registered force callback.
	 *
	 * @param forceInducer callback to remove
	 * @return {@code true} when the callback was registered
	 */
	default boolean removeForceInducer(IShipForcesInducer forceInducer) {
		return false;
	}

	/**
	 * Remove all force callbacks invoked on a ship
	 * */
	default void clearForceInducers() {}

	/**
	 * Registers a component that can keep this physics entity assembled.
	 * Providers are transient and must be registered again after the ship is loaded.
	 *
	 * @param provider provider to register
	 * @return {@code true} when the provider was newly registered
	 * */
	default boolean addMaintenanceProvider(@NotNull IShipMaintenanceProvider provider) {
		throw new UnsupportedOperationException(
				"This physics entity does not support maintenance providers"
		);
	}

	/**
	 * Unregisters a component that was maintaining this physics entity.
	 *
	 * @param provider provider to unregister
	 * @return {@code true} when the provider was registered
	 * */
	default boolean removeMaintenanceProvider(@NotNull IShipMaintenanceProvider provider) {
		return false;
	}

	/**
	 * Checks whether at least one registered provider can currently maintain this physics entity.
	 *
	 * @return {@code true} when a registered provider reports that it is active
	 * */
	default boolean hasActiveMaintenanceProvider() {
		return false;
	}
}
