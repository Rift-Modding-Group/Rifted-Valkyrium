package org.valkyrienskies.mod.common.ships.ship_world;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.List;
import java.util.Optional;

/**
 * Resolves particle coordinates to the moving ship frame that owns them
 * */
public class ShipParticleResolver {
    @Nullable
    public static ResolvedShip resolve(@NotNull World world, double x, double y, double z) {
        Optional<PhysicsObject> localShip = ValkyrienUtils.getPhysoManagingBlock(world, new BlockPos(x, y, z));
        if (localShip.isPresent()) return new ResolvedShip(localShip.get(), true);

        PhysicsObject worldShip = findWorldShip(world, x, y, z);
        return worldShip == null ? null : new ResolvedShip(worldShip, false);
    }

    @Nullable
    public static PhysicsObject findWorldShip(@NotNull World world, double x, double y, double z) {
        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        if (physObjectWorld == null) return null;

        PhysicsObject closestShip = null;
        double closestDistance = Double.MAX_VALUE;
        boolean nearShip = false;
        for (PhysicsObject ship : physObjectWorld.getAllLoadedPhysObj()) {
            double localDistance = distanceSquaredFromShip(ship, x, y, z);
            if (localDistance <= 1024D) nearShip = true;
            if (localDistance > 16D) continue;

            if (localDistance < closestDistance) {
                closestShip = ship;
                closestDistance = localDistance;
            }
        }
        if (closestShip != null) return closestShip;
        if (!nearShip) return null;

        AxisAlignedBB sourceSearch = new AxisAlignedBB(x, y, z, x, y, z).grow(2D);
        List<Entity> possibleSources = world.getEntitiesWithinAABB(Entity.class, sourceSearch);
        for (Entity possibleSource : possibleSources) {
            ShipData shipData = EntityDraggable.getActiveShip(possibleSource);
            if (shipData == null) continue;

            PhysicsObject ship = physObjectWorld.getPhysObjectFromUUID(shipData.getUuid());
            if (ship != null && distanceSquaredFromShip(ship, x, y, z) <= 1024D) return ship;
        }
        return null;
    }

    private static double distanceSquaredFromShip(PhysicsObject ship, double x, double y, double z) {
        if (ship.getBlockPositions() == null) return 0D;
        AxisAlignedBB localBounds = ship.getBlockPositions().makeAABB();
        if (localBounds == null) return distanceSquared(ship.getShipBB(), x, y, z);

        Vector3d localPosition = new Vector3d(x, y, z);
        ship.getShipTransformationManager().getRenderTransform()
                .transformPosition(localPosition, TransformType.GLOBAL_TO_SUBSPACE);
        return distanceSquared(localBounds, localPosition.x, localPosition.y, localPosition.z);
    }

    private static double distanceSquared(AxisAlignedBB bounds, double x, double y, double z) {
        double distanceX = Math.max(Math.max(bounds.minX - x, 0D), x - bounds.maxX);
        double distanceY = Math.max(Math.max(bounds.minY - y, 0D), y - bounds.maxY);
        double distanceZ = Math.max(Math.max(bounds.minZ - z, 0D), z - bounds.maxZ);
        return distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ;
    }

    public record ResolvedShip(@NotNull PhysicsObject ship, boolean localCoordinates) {}
}
