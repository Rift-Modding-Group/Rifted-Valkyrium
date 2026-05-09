package org.valkyrienskies.addon.control.capability.controlNodeUser;

import net.minecraft.util.math.BlockPos;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

public interface ICapabilityControlNodeUser {
    PhysicsObject getShip();
    void setShip(PhysicsObject physicsObject);
    BlockPos getUsedControlNodePos();
    void setUsedControlNodePos(BlockPos pos);
    void stopUsingEverything();

    void onClientTick();
}
