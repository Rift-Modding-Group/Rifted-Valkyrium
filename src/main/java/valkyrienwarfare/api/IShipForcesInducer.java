package valkyrienwarfare.api;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * Supplies forces to a loaded ship on every physics step.
 * <p>
 * Callbacks run on Rifted Valkyrium's physics thread. Implementations must be thread-safe and must
 * not read or modify Minecraft world state from this callback. Data shared with the game thread
 * should be copied into thread-safe fields before the callback runs.
 */
@FunctionalInterface
@ParametersAreNonnullByDefault
public interface IShipForcesInducer {
    /**
     * Adds forces for the current physics step.
     *
     * @param forces the current ship and its force accumulator
     */
    void applyForces(IShipForces forces);

    /**
     * Apply a force around after all other force inducers have applied their forces
     * Inducers that need to know the total accumulated forces must use this.
     */
    default boolean applyForceLate() {
        return false;
    }
}