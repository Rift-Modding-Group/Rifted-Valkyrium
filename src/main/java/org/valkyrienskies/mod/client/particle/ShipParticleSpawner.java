package org.valkyrienskies.mod.client.particle;

import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.Particle;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.ShipParticleResolver;
import org.valkyrienskies.mod.common.ships.ship_world.ShipParticleResolver.ResolvedShip;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

/**
 * Creates particles in moving ship frames while preserving vanilla visibility settings
 * */
public class ShipParticleSpawner {
    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public static boolean spawnForEntity(
            @NotNull Entity source, @NotNull EnumParticleTypes particleType,
            double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
            int... parameters
    ) {
        ShipData shipData = EntityDraggable.getActiveShip(source);
        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(source.world);
        PhysicsObject ship = shipData == null || physObjectWorld == null
                ? null : physObjectWorld.getPhysObjectFromUUID(shipData.getUuid());
        if (ship == null) return false;

        spawn(source.world, ship, particleType, x, y, z, xSpeed, ySpeed, zSpeed, parameters);
        return true;
    }

    @NotNull
    public static SpawnResult spawnIfShip(
            @NotNull World world, int particleId, boolean ignoreRange, boolean minimiseParticleLevel,
            double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
            int... parameters
    ) {
        ResolvedShip resolvedShip = ShipParticleResolver.resolve(world, x, y, z);
        if (resolvedShip == null) return new SpawnResult(false, null);

        Particle particle = spawn(
                world, resolvedShip.ship(), particleId, ignoreRange,
                minimiseParticleLevel, resolvedShip.localCoordinates(),
                x, y, z, xSpeed, ySpeed, zSpeed, parameters
        );
        return new SpawnResult(true, particle);
    }

    public static void spawn(
            @NotNull World world, @NotNull PhysicsObject ship, @NotNull EnumParticleTypes particleType,
            double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed,
            int... parameters
    ) {
        spawn(
                world, ship, particleType.getParticleID(), particleType.getShouldIgnoreRange(),
                false, false,
                x, y, z, xSpeed, ySpeed, zSpeed, parameters
        );
    }

    @Nullable
    public static Particle spawnLocal(
            @NotNull World world, @NotNull PhysicsObject ship, int particleId, boolean ignoreRange,
            double x, double y, double z, double xSpeed, double ySpeed, double zSpeed, int... parameters
    ) {
        return spawn(
                world, ship, particleId, ignoreRange, false, true,
                x, y, z, xSpeed, ySpeed, zSpeed, parameters
        );
    }

    public static void transformToLocal(@NotNull PhysicsObject ship, int particleId, @NotNull Vector3d position, @NotNull Vector3d velocity) {
        ShipTransform renderTransform = ship.getShipTransformationManager().getRenderTransform();
        renderTransform.transformPosition(position, TransformType.GLOBAL_TO_SUBSPACE);
        if (shouldTransformVelocity(particleId)) {
            renderTransform.transformDirection(velocity, TransformType.GLOBAL_TO_SUBSPACE);
        }
    }

    public static boolean shouldTransformVelocity(int particleId) {
        EnumParticleTypes particleType = EnumParticleTypes.getParticleFromId(particleId);
        if (particleType == null) return true;

        return switch (particleType) {
            case NOTE, REDSTONE, SPELL_MOB, SPELL_MOB_AMBIENT -> false;
            default -> true;
        };
    }

    @Nullable
    private static Particle spawn(
            @NotNull World world, @NotNull PhysicsObject ship, int particleId,
            boolean ignoreRange, boolean minimiseParticleLevel, boolean localCoordinates,
            double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
            int... parameters
    ) {
        Minecraft minecraft = Minecraft.getMinecraft();
        Entity camera = minecraft.getRenderViewEntity();
        if (camera == null || minecraft.effectRenderer == null) return null;

        ShipTransform renderTransform = ship.getShipTransformationManager().getRenderTransform();
        Vector3d localPosition = new Vector3d(x, y, z);
        Vector3d localVelocity = new Vector3d(xSpeed, ySpeed, zSpeed);
        Vector3d worldPosition = new Vector3d(x, y, z);
        if (localCoordinates) renderTransform.transformPosition(worldPosition, TransformType.SUBSPACE_TO_GLOBAL);
        else transformToLocal(ship, particleId, localPosition, localVelocity);

        int particleLevel = minecraft.gameSettings.particleSetting;
        if (minimiseParticleLevel && particleLevel == 2 && world.rand.nextInt(10) == 0) particleLevel = 1;
        if (particleLevel == 1 && world.rand.nextInt(3) == 0) particleLevel = 2;

        double distanceX = camera.posX - worldPosition.x;
        double distanceY = camera.posY - worldPosition.y;
        double distanceZ = camera.posZ - worldPosition.z;
        double distanceSquared = distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ;
        if (!ignoreRange && (distanceSquared > 1024D || particleLevel > 1)) return null;

        return minecraft.effectRenderer.spawnEffectParticle(
                particleId,
                localPosition.x, localPosition.y, localPosition.z,
                localVelocity.x, localVelocity.y, localVelocity.z,
                parameters
        );
    }

    public record SpawnResult(boolean handled, @Nullable Particle particle) {}
}
