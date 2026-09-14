package org.valkyrienskies.mod.common.ships.ship_world;

import net.minecraft.world.World;

import java.util.function.Function;

/**
 * Legacy world ship-manager access retained for binary compatibility with integrations written
 * against Valkyrien Skies 1.
 *
 * @deprecated Use the ship-world capability instead.
 */
@Deprecated
public interface IHasShipManager {
    IPhysObjectWorld getManager();

    void setManager(Function<World, IPhysObjectWorld> managerSupplier);
}
