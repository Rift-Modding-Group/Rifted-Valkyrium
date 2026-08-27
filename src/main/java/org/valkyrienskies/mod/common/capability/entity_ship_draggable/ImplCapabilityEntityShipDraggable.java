package org.valkyrienskies.mod.common.capability.entity_ship_draggable;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;
import org.valkyrienskies.mod.common.ships.ShipData;

public class ImplCapabilityEntityShipDraggable implements IEntityShipDraggable {
    @Nullable
    private ShipData lastTouchedShip;
    private int ticksSinceTouchedShip;
    private int ticksPartOfGround;
    @NotNull
    private Vector3dc addedLinearVelocity = new Vector3d();
    private double addedYawVelocity;
    private int ticksInAirPocket;
    private boolean shipLocalRenderSyncActive;

    //---ship contact info---
    @Override
    @Nullable
    public ShipData getLastTouchedShip() {
        return this.lastTouchedShip;
    }

    @Override
    public void setLastTouchedShip(@Nullable ShipData lastTouchedShip) {
        this.lastTouchedShip = lastTouchedShip;
    }

    @Override
    public int getTicksSinceTouchedShip() {
        return this.ticksSinceTouchedShip;
    }

    @Override
    public void setTicksSinceTouchedShip(int value) {
        this.ticksSinceTouchedShip = value;
    }

    @Override
    public int getTicksPartOfGround() {
        return this.ticksPartOfGround;
    }

    @Override
    public void setTicksPartOfGround(int value) {
        this.ticksPartOfGround = value;
    }

    @Override
    @NotNull
    public Vector3dc getAddedLinearVelocity() {
        return this.addedLinearVelocity;
    }

    @Override
    public void setAddedLinearVelocity(@NotNull Vector3dc vector) {
        this.addedLinearVelocity = vector;
    }

    @Override
    public double getAddedYawVelocity() {
        return this.addedYawVelocity;
    }

    @Override
    public void setAddedYawVelocity(double value) {
        this.addedYawVelocity = value;
    }

    //---syncing server to client---
    @Override
    public boolean isShipLocalRenderSyncActive() {
        return this.shipLocalRenderSyncActive;
    }

    @Override
    public void setShipLocalRenderSyncActive(boolean active) {
        this.shipLocalRenderSyncActive = active;
    }

    //---other stuff---
    @Override
    public boolean getInAirPocket() {
        return this.ticksInAirPocket > 0;
    }

    @Override
    public void setTicksAirPocket(int ticksInAirPocket) {
        this.ticksInAirPocket = ticksInAirPocket;
    }

    @Override
    public void decrementTicksAirPocket() {
        this.ticksInAirPocket--;
    }
}
