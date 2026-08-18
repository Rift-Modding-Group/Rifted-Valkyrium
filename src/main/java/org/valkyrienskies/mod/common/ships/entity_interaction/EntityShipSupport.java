package org.valkyrienskies.mod.common.ships.entity_interaction;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.entity.EntityShipMovementData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_world.WorldServerShipManager;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

/**
 * helper class w checks for determining whether an entity has standing contact with a ship.
 */
public final class EntityShipSupport {
    /**
     * Keeps render synchronization active while either Minecraft still reports the
     * standing contact or the active backend's support lease is carrying the entity.
     */
    public static boolean isActivelySupportedByShip(@NotNull Entity entity, @NotNull ShipData shipData) {
        if (entity.world.isRemote) return false;

        EntityShipMovementData movementData = getRecentShipContact(entity, shipData);
        if (movementData == null) return false;

        WorldServerShipManager serverShipManager = (WorldServerShipManager) ValkyrienUtils.getPhysObjWorld(entity.world);
        if (serverShipManager == null) return false;

        return movementData.isStandingOnShip() || serverShipManager.getPhysicsLoop().isPhysicsEntitySupportedByShip(entity, shipData.getUuid());
    }

    /**
     * Uses the ship collision injector's standable-normal result instead of
     * reprojecting block collision boxes after the collision has been resolved.
     */
    public static boolean hasStandingContact(@NotNull Entity entity, @NotNull ShipData shipData) {
        EntityShipMovementData movementData = getRecentShipContact(entity, shipData);
        return movementData != null && movementData.isStandingOnShip();
    }

    public static boolean hasRecentShipContact(@NotNull Entity entity, @NotNull ShipData shipData) {
        return getRecentShipContact(entity, shipData) != null;
    }

    @Nullable
    private static EntityShipMovementData getRecentShipContact(@NotNull Entity entity, @NotNull ShipData shipData) {
        IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable == null) return null;

        EntityShipMovementData movementData = draggable.getEntityShipMovementData();
        if (movementData != null
                && movementData.getLastTouchedShip() != null
                && movementData.getTicksSinceTouchedShip() < VSConfig.ticksToStickToShip
                && movementData.getLastTouchedShip().getUuid().equals(shipData.getUuid())) {
            return movementData;
        }
        return null;
    }
}
