package org.valkyrienskies.mod.common.ships.ship_world;

import net.minecraft.world.World;

import java.util.function.Function;

/**
 * Used to get the ship manager from world objects.
 *
 * TODO: turn into a world capability.
 */
@Deprecated
public interface IHasShipManager {

    IPhysObjectWorld getManager();

    void setManager(Function<World, IPhysObjectWorld> managerSupplier);

}
