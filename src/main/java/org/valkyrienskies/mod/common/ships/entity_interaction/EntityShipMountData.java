package org.valkyrienskies.mod.common.ships.entity_interaction;

import net.minecraft.util.math.Vec3d;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

/**
 * helper for items that attach a player to a block on a ship
 * like sittables or beds
 * */
public record EntityShipMountData(boolean isMounted, PhysicsObject mountedShip, Vec3d mountPos) {
    public EntityShipMountData() {
        this(false, null, null);
    }

    public EntityShipMountData(PhysicsObject physicsObject, Vec3d mountPos) {
        this(true, physicsObject, mountPos);
    }
}
