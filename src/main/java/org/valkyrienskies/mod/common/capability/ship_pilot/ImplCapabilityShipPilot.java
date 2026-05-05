package org.valkyrienskies.mod.common.capability.ship_pilot;

import net.minecraft.util.math.BlockPos;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.piloting.ControllerInputType;
import org.valkyrienskies.mod.common.piloting.PilotControlsMessage;
import org.valkyrienskies.mod.common.piloting.PilotControlsMessageNew;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.UUID;

public class ImplCapabilityShipPilot implements IShipPilot {
    private PhysicsObject pilotedShip;
    private BlockPos blockBeingControlled;
    private ControllerInputType controlInputType;
    private UUID pilotedShipID;

    @Override
    public PhysicsObject getPilotedShip() {
        return this.pilotedShip;
    }

    @Override
    public void setPilotedShip(PhysicsObject physicsObject) {
        this.pilotedShip = physicsObject;
    }

    @Override
    public boolean isPilotingShip() {
        return this.pilotedShip != null || this.pilotedShipID != null;
    }

    @Override
    public boolean isPilotingATile() {
        return this.blockBeingControlled != null;
    }

    @Override
    public boolean isPiloting() {
        return this.isPilotingATile();
    }

    @Override
    public BlockPos getPosBeingControlled() {
        return this.blockBeingControlled;
    }

    @Override
    public void setPosBeingControlled(BlockPos pos) {
        this.blockBeingControlled = pos;
    }

    @Override
    public UUID getShipIDBeingControlled() {
        return this.pilotedShipID;
    }

    @Override
    public void setShipIDBeingControlled(UUID shipID) {
        this.pilotedShipID = shipID;
    }

    @Override
    public ControllerInputType getControllerInputEnum() {
        return this.controlInputType;
    }

    @Override
    public void setControllerInputEnum(ControllerInputType type) {
        this.controlInputType = type;
    }

    @Override
    public void stopPilotingEverything() {
        this.setPilotedShip(null);
        this.setShipIDBeingControlled(null);
        this.setPosBeingControlled(null);
        this.setControllerInputEnum(null);
    }

    @Override
    public void onClientTick() {
        if (this.isPilotingShip() || this.isPilotingATile()) {
            final UUID shipId;
            if (getPilotedShip() != null) {
                shipId = getPilotedShip().getUuid();
            }
            else shipId = getShipIDBeingControlled();
            this.sendPilotKeysToServer(this.getControllerInputEnum(), shipId);
        }
    }

    private void sendPilotKeysToServer(ControllerInputType type, UUID shipPiloting) {
        ValkyrienSkiesMod.controlNetwork.sendToServer(new PilotControlsMessageNew(shipPiloting, this.getPosBeingControlled()));
    }
}
