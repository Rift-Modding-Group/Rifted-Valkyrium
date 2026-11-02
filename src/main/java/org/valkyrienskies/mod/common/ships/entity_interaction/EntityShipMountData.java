package org.valkyrienskies.mod.common.ships.entity_interaction;

import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

/**
 * helper for items that attach a player to a block on a ship
 * like sittables or beds
 * */
public record EntityShipMountData(boolean isMounted, @Nullable PhysicsObject mountedShip, @Nullable Vec3d mountPos) {
    public EntityShipMountData() {
        this(false, null, null);
    }

    public EntityShipMountData(@NotNull PhysicsObject physicsObject, @NotNull Vec3d mountPos) {
        this(true, physicsObject, mountPos);
    }

    /**
     * @deprecated Like come on (ps its here for compat)
     * */
    @Deprecated
    public PhysicsObject getMountedShip() {
        return this.mountedShip;
    }
}
