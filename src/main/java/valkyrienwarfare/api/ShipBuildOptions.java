package valkyrienwarfare.api;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;

/**
 * This builder-like class stores additional options for ship-building
 * */
@ParametersAreNonnullByDefault
public final class ShipBuildOptions {
    @NotNull
    private ShipBlockBlacklist shipBlockBlacklist = ShipBlockBlacklist.airOnly();
    @Nullable
    private String shipName;
    private boolean physicsEnabled;

    /**
     * Set a blacklist for the blocks that Rifted Valkyrium will ignore when building a {@link IPhysicsEntity}.
     * By default, it will use a simple blacklist that just blocks air blocks.
     * */
    public ShipBuildOptions setShipBlockBlacklist(ShipBlockBlacklist shipBlockBlacklist) {
        this.shipBlockBlacklist = shipBlockBlacklist;
        return this;
    }

    @NotNull
    public ShipBlockBlacklist getShipBlockBlacklist() {
        return this.shipBlockBlacklist;
    }

    /**
     * This will assign a name to your ship.
     * By default, a random name will be generated.
     * */
    public ShipBuildOptions setShipName(String shipName) {
        this.shipName = shipName;
        return this;
    }

    @Nullable
    public String getShipName() {
        return this.shipName;
    }

    /**
     * This makes it so that physics is enabled the moment that a {@link IPhysicsEntity} is created.
     * */
    public ShipBuildOptions setPhysicsEnabled() {
        this.physicsEnabled = true;
        return this;
    }

    public boolean getPhysicsEnabled() {
        return this.physicsEnabled;
    }
}
