package org.valkyrienskies.mod.client.entity_position;

import net.minecraft.entity.Entity;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class EntityRenderPositionManager {
    // Used to store entity position variables, that way we can restore them to their original values after
    // the rendering code has finished.
    private static final WeakHashMap<Entity, EntityRenderPositionBackup> renderPositionBackups = new WeakHashMap<>();

    // Stores server-provided ship-local render positions for entities so client rendering can
    // apply the ship's current render transform instead of vanilla global interpolation.
    private static final WeakHashMap<Entity, ShipLocalEntityRenderData> shipLocalEntityRenderData = new WeakHashMap<>();
    private static long lastShipLocalRenderDataPromotionTick = Long.MIN_VALUE;

    public static void clearRenderPositionBackups() {
        renderPositionBackups.clear();
    }

    public static void restoreRenderPositionBackups(final World world) {
        for (final Entity entity : world.getLoadedEntityList()) {
            if (renderPositionBackups.containsKey(entity)) {
                renderPositionBackups.get(entity).restore(entity);
            }
        }
    }

    public static void promoteShipLocalRenderData(final World world) {
        final long worldTick = world.getTotalWorldTime();
        if (lastShipLocalRenderDataPromotionTick == worldTick) return;

        lastShipLocalRenderDataPromotionTick = worldTick;

        final Iterator<Map.Entry<Entity, ShipLocalEntityRenderData>> iterator = shipLocalEntityRenderData.entrySet().iterator();
        while (iterator.hasNext()) {
            final ShipLocalEntityRenderData renderData = iterator.next().getValue();
            renderData.promoteQueuedUpdate(worldTick);
            if (renderData.isExpired(world)) {
                iterator.remove();
            }
        }
    }

    public static void removeShipLocalRenderData(final Entity entity) {
        shipLocalEntityRenderData.remove(entity);
    }

    public static void queueShipLocalRenderData(
            final Entity entity, final UUID shipUuid, final Vector3dc localPosition, final long updateTick
    ) {
        ShipLocalEntityRenderData renderData = shipLocalEntityRenderData.get(entity);
        if (renderData == null) {
            renderData = new ShipLocalEntityRenderData(shipUuid, localPosition, updateTick);
            shipLocalEntityRenderData.put(entity, renderData);
        }
        else {
            renderData.queueUpdate(shipUuid, localPosition);
        }
    }

    /**
     * Applies queued ship-local render data by transforming it through the ship's current render transform.
     * Returns true when the entity was temporarily moved for this render pass.
     */
    public static boolean applyShipLocalRenderPosition(
            final Entity entity, final World world,
            final IPhysObjectWorld physObjectWorld, final double partialTicks
    ) {
        //dont apply if riding on chair entity
        if (entity.isRiding() && ValkyrienUtils.getMountedShipAndPos(entity).isMounted()) {
            return false;
        }

        //doesnt apply if no render data
        final ShipLocalEntityRenderData renderData = shipLocalEntityRenderData.get(entity);
        if (renderData == null) return false;

        //or if render data is expired
        if (renderData.isExpired(world)) {
            shipLocalEntityRenderData.remove(entity);
            return false;
        }

        //or if ship associated w render data doesn't exist
        final PhysicsObject shipPhysicsObject = physObjectWorld.getPhysObjectFromUUID(renderData.getShipUuid());
        if (shipPhysicsObject == null) {
            shipLocalEntityRenderData.remove(entity);
            return false;
        }

        final Vector3d renderPosition = renderData.getInterpolatedLocalPosition(partialTicks);
        shipPhysicsObject.getShipTransformationManager()
                .getRenderTransform()
                .transformPosition(renderPosition, TransformType.SUBSPACE_TO_GLOBAL);

        backupEntityRenderPosition(entity);
        setEntityRenderPosition(entity, renderPosition);
        return true;
    }

    public static void backupEntityRenderPosition(final Entity entity) {
        if (!renderPositionBackups.containsKey(entity)) {
            renderPositionBackups.put(entity, EntityRenderPositionBackup.of(entity));
        }
    }

    public static void setEntityRenderPosition(final Entity entity, final Vector3dc position) {
        final double deltaX = position.x() - entity.posX;
        final double deltaY = position.y() - entity.posY;
        final double deltaZ = position.z() - entity.posZ;

        entity.posX = position.x();
        entity.posY = position.y();
        entity.posZ = position.z();
        entity.lastTickPosX = position.x();
        entity.lastTickPosY = position.y();
        entity.lastTickPosZ = position.z();
        entity.setEntityBoundingBox(entity.getEntityBoundingBox().offset(deltaX, deltaY, deltaZ));
    }
}
