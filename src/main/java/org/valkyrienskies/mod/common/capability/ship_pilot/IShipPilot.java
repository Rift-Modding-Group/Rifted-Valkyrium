package org.valkyrienskies.mod.common.capability.ship_pilot;

import net.minecraft.util.math.BlockPos;
import org.valkyrienskies.mod.common.piloting.ControllerInputType;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.UUID;

/*
 * TODO: this class is to be changed so that its only for players that
 *  are directly controlling the ship (aka from the pilots chair.
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

    //todo: remove this alongside ControllerInputType
    @Deprecated
    ControllerInputType getControllerInputEnum();

    //todo: remove this too
    @Deprecated
    void setControllerInputEnum(ControllerInputType type);

    void stopPilotingEverything();

    void onClientTick();
}
