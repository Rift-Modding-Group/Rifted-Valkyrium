package org.valkyrienskies.mod.common.capability.ship_pilot;

import net.minecraft.util.math.BlockPos;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.UUID;

/*
 * TODO: this class is to be changed so that its only for players that
 *  are directly controlling the ship (ex: from the captains chair).
 */
public interface IShipPilot {
    PhysicsObject getPilotedShip();

    void setPilotedShip(PhysicsObject physicsObject);

    boolean isPilotingShip();

    boolean isPilotingATile();

    boolean isPiloting();

    BlockPos getPosBeingControlled();

    void setPosBeingControlled(BlockPos pos);

    UUID getShipIDBeingControlled();

    void setShipIDBeingControlled(UUID shipID);

    void stopPilotingEverything();

    void onClientTick();
}
