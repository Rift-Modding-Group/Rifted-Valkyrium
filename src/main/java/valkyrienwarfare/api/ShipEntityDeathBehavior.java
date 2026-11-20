package valkyrienwarfare.api;

/**
 * Determines what happens to a linked ship when its carrier dies.
 */
public enum ShipEntityDeathBehavior {
    /**
     * Keeps the ship and releases it from the carrier.
     * */
    DETACH,
    /**
     * Breaks the ship's blocks with drops.
     * */
    BREAK_BLOCKS,
    /**
     * Deletes the ship, its blocks, and their contents without drops.
     * */
    DELETE_SHIP,
    /**
     * Saves and unloads the ship while its carrier is absent, retaining the link for its return.
     * */
    UNLOAD
}
