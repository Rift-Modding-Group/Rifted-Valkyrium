package org.valkyrienskies.mod.common.ships.entity_interaction;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.config.VSConfig;
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

        IEntityShipDraggable draggable = getRecentShipContact(entity, shipData);
        if (draggable == null) return false;

        WorldServerShipManager serverShipManager = (WorldServerShipManager) ValkyrienUtils.getPhysObjWorld(entity.world);
        if (serverShipManager == null) return false;

        return draggable.isStandingOnShip() || serverShipManager.getPhysicsLoop().isPhysicsEntitySupportedByShip(entity, shipData.getUuid());
    }

    /**
     * Uses the ship collision injector's standable-normal result instead of
     * reprojecting block collision boxes after the collision has been resolved.
     */
    public static boolean hasStandingContact(@NotNull Entity entity, @NotNull ShipData shipData) {
        IEntityShipDraggable draggable = getRecentShipContact(entity, shipData);
        return draggable != null && draggable.isStandingOnShip();
    }

    public static boolean hasRecentShipContact(@NotNull Entity entity, @NotNull ShipData shipData) {
        return getRecentShipContact(entity, shipData) != null;
    }

    @Nullable
    private static IEntityShipDraggable getRecentShipContact(@NotNull Entity entity, @NotNull ShipData shipData) {
        IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable == null) return null;

        ShipData lastTouchedShip = draggable.getLastTouchedShip();
        if (lastTouchedShip != null
                && draggable.getTicksSinceTouchedShip() < VSConfig.ticksToStickToShip
                && lastTouchedShip.getUuid().equals(shipData.getUuid())) {
            return draggable;
        }
        return null;
    }
}
