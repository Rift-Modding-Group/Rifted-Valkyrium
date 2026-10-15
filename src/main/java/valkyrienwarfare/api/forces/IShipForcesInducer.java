package valkyrienwarfare.api.forces;

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
     * Lower values -> higher priority and will run first.
     * Inducers that need to know the total accumulated forces must have a lower priority.
     */
    default int getForceApplicationPriority() {
        return 0;
    }
}