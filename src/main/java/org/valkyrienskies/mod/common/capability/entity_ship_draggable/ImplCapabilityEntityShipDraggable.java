package org.valkyrienskies.mod.common.capability.entity_ship_draggable;

import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.jspecify.annotations.Nullable;
import org.valkyrienskies.mod.common.ships.ShipData;

import java.util.UUID;

public class ImplCapabilityEntityShipDraggable implements IEntityShipDraggable {
    @Nullable
    private ShipData lastTouchedShip;
    private int ticksSinceTouchedShip;
    private int ticksPartOfGround;
    @NotNull
    private Vector3dc addedLinearVelocity = new Vector3d();
    private double addedYawVelocity;
    @Nullable
    private UUID pendingShipId;
    @Nullable
    private Vector3dc pendingShipLocalPosition;
    private int ticksInAirPocket;
    @Nullable
    private ShipLocalEntityMovementData shipLocalMovementData;

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

    //---position restored after loading---
    @Override
    @Nullable
    public UUID getPendingShipId() {
        return this.pendingShipId;
    }

    @Override
    @Nullable
    public Vector3dc getPendingShipLocalPosition() {
        return this.pendingShipLocalPosition;
    }

    @Override
    public void setPendingShipPosition(@Nullable UUID shipId, @Nullable Vector3dc localPosition) {
        this.pendingShipId = shipId;
        this.pendingShipLocalPosition = localPosition == null ? null : new Vector3d(localPosition);
    }

    @Override
    public void clearPendingShipPosition() {
        this.pendingShipId = null;
        this.pendingShipLocalPosition = null;
    }

    //---server-controlled movement in ship coordinates---
    @Override
    @Nullable
    public ShipLocalEntityMovementData getShipLocalMovementData() {
        return this.shipLocalMovementData;
    }

    @Override
    @NotNull
    public ShipLocalEntityMovementData getOrCreateShipLocalMovementData() {
        if (this.shipLocalMovementData == null) {
            this.shipLocalMovementData = new ShipLocalEntityMovementData();
        }
        return this.shipLocalMovementData;
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
