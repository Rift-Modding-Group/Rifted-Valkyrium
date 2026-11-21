package valkyrienwarfare.api;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
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
    private ShipBlockBlacklist shipBlockAssembledBlacklist;
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
    private boolean dontSetSpawn;

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
     * Set a blacklist for the blocks that cannot be placed on an active {@link IPhysicsEntity}.
     * By default, it's null, so that anything is allowed.
     * */
    public ShipBuildOptions setShipBlockAssembledBlacklist(@Nullable ShipBlockBlacklist shipBlockAssembledBlacklist) {
        this.shipBlockAssembledBlacklist = shipBlockAssembledBlacklist;
        return this;
    }

    @Nullable
    public ShipBlockBlacklist getShipBlockAssembledBlacklist() {
        return this.shipBlockAssembledBlacklist;
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
     * Defines equal X/Z build limits and a height, anchored at the bottom-center assembly block.
     * See {@link #setMaxShipBounds(int, int, int)} for alignment and examples.
     * */
    public ShipBuildOptions setMaxShipBounds(int horizontalSize, int verticalSize) {
        return this.setMaxShipBounds(horizontalSize, verticalSize, horizontalSize);
    }

    /**
     * Defines fixed build limits in blocks, used during assembly and for later block placement.
     * The assembly seed is the bottom-center block: no blocks may be added below it, and
     * {@code ySize} includes its own layer. Each horizontal axis starts at
     * {@code seed - size / 2} (integer division) and contains exactly {@code size} blocks.
     * Even sizes therefore have one more block on the negative side of the seed.
     * <p>
     * A centered 3 x 1 x 3 ship with 7 x 7 x 7 limits can grow two blocks on each horizontal
     * side and six blocks above, with none below. With 6 x 6 x 6 limits it can grow two
     * blocks toward negative X/Z, one toward positive X/Z, and five above.
     * These limits stay anchored when blocks are added or removed.
     * <p>
     * If unset, there are no additional build bounds. The maximum block count and ship
     * storage limits still apply.
     *
     * @throws IllegalArgumentException if any dimension is not positive
     */
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

    /**
     * An optional RiftLibrary locator makes the offset relative to its animated pose.
     * */
    public ShipBuildOptions setEntityPeggedTo(Entity entity, Vec3d peggedEntityOffset, ShipEntityDeathBehavior entityDeathBehavior, @Nullable String locator) {
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

    /**
     * If set, this makes it so sleeping on ships will not set the spawnpoint to there
     * */
    public ShipBuildOptions setDontSetSpawn() {
        this.dontSetSpawn = true;
        return this;
    }

    public boolean getDontSetSpawn() {
        return this.dontSetSpawn;
    }
}
