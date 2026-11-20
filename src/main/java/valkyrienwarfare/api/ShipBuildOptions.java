package valkyrienwarfare.api;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Objects;

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
    private int @Nullable [] maxShipBounds;
    @Nullable
    private Entity peggedEntity;
    @Nullable
    private Vec3d peggedEntityOffset;
    @Nullable
    private String peggedEntityLocator;
    @NotNull
    private ShipEntityDeathBehavior entityDeathBehavior = ShipEntityDeathBehavior.DETACH;

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
     * If not set, physics has to be enabled some other way.
     * */
    public ShipBuildOptions setPhysicsEnabled() {
        this.physicsEnabled = true;
        return this;
    }

    public boolean getPhysicsEnabled() {
        return this.physicsEnabled;
    }

    /**
     * Defines the bounds of the ship. If not set, its presumed to be infinite.
     * Note that the maximum block count remains in effect, and has the final say on ship sizes.
     * */
    public ShipBuildOptions setMaxShipBounds(int horizontalSize, int verticalSize) {
        return this.setMaxShipBounds(horizontalSize, verticalSize, horizontalSize);
    }

    public ShipBuildOptions setMaxShipBounds(int xSize, int ySize, int zSize) {
        if (xSize <= 0 || ySize <= 0 || zSize <= 0) {
            throw new IllegalArgumentException("Ship bounds must have positive dimensions");
        }
        this.maxShipBounds = new int[]{xSize, ySize, zSize};
        return this;
    }

    public int @Nullable [] getMaxShipBounds() {
        if (this.maxShipBounds == null) return null;
        return this.maxShipBounds.clone();
    }

    /**
     * This pegs this ship to an entity. This means that it is attached to said entity.
     * */
    public ShipBuildOptions setEntityPeggedTo(Entity entity) {
        return this.setEntityPeggedTo(entity, Vec3d.ZERO, ShipEntityDeathBehavior.DETACH);
    }

    /**
     * This pegs this ship to an entity. This means that it is attached to said entity.
     * By default it uses the position of the entity, but it can be offsetted.
     * */
    public ShipBuildOptions setEntityPeggedTo(Entity entity, Vec3d peggedEntityOffset) {
        return this.setEntityPeggedTo(entity, peggedEntityOffset, ShipEntityDeathBehavior.DETACH);
    }

    public ShipBuildOptions setEntityPeggedTo(Entity entity, ShipEntityDeathBehavior entityDeathBehavior) {
        return this.setEntityPeggedTo(entity, Vec3d.ZERO, entityDeathBehavior);
    }

    /**
     * Chooses whether the carrier's death detaches the ship, breaks its blocks, or deletes it.
     */
    public ShipBuildOptions setEntityPeggedTo(Entity entity, Vec3d peggedEntityOffset, ShipEntityDeathBehavior entityDeathBehavior) {
        return this.setEntityPeggedTo(entity, peggedEntityOffset, entityDeathBehavior, null);
    }

    /** An optional RiftLibrary locator makes the offset relative to its animated pose. */
    public ShipBuildOptions setEntityPeggedTo(Entity entity, Vec3d peggedEntityOffset, ShipEntityDeathBehavior entityDeathBehavior,
                                            @Nullable String locator) {
        this.peggedEntity = entity;
        this.peggedEntityOffset = peggedEntityOffset;
        this.entityDeathBehavior = entityDeathBehavior;
        this.peggedEntityLocator = locator;
        return this;
    }

    @Nullable
    public String getPeggedEntityLocator() {
        return this.peggedEntityLocator;
    }

    @Nullable
    public Entity getPeggedEntity() {
        return this.peggedEntity;
    }

    @Nullable
    public Vec3d getPeggedEntityOffset() {
        return this.peggedEntityOffset;
    }

    @NotNull
    public ShipEntityDeathBehavior getEntityDeathBehavior() {
        return this.entityDeathBehavior;
    }
}
