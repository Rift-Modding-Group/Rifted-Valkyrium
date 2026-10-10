package org.valkyrienskies.mixin.client.entity;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

/**
 * Creates sprinting particles in the ship coordinates for when
 * sprinting entity is, well, sprinting on top of it.
 */
@Mixin(Entity.class)
public abstract class MixinEntityRunningParticles {
    @Redirect(method = "createRunningParticles", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/World;spawnParticle(Lnet/minecraft/util/EnumParticleTypes;DDDDDD[I)V"))
    private void spawnRunningParticle(
            World world, EnumParticleTypes particleType,
            double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
            int... parameters
    ) {
        Entity entity = (Entity) (Object) this;
        ShipData touchedShip = ValkyrienUtils.getLastShipTouchedByEntity(entity);
        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        PhysicsObject ship = null;
        if (touchedShip != null && physObjectWorld != null) {
            ship = physObjectWorld.getPhysObjectFromUUID(touchedShip.getUuid());
        }

        if (ship == null) {
            world.spawnParticle(particleType, x, y, z, xSpeed, ySpeed, zSpeed, parameters);
            return;
        }

        Minecraft minecraft = Minecraft.getMinecraft();
        Entity camera = minecraft.getRenderViewEntity();
        if (camera == null || minecraft.effectRenderer == null) return;

        double distanceX = camera.posX - x;
        double distanceY = camera.posY - y;
        double distanceZ = camera.posZ - z;
        if (distanceX * distanceX + distanceY * distanceY + distanceZ * distanceZ > 1024D) return;

        int particleLevel = minecraft.gameSettings.particleSetting;
        if (particleLevel == 1 && world.rand.nextInt(3) == 0) particleLevel = 2;
        if (particleLevel > 1) return;

        ShipTransform renderTransform = ship.getShipTransformationManager().getRenderTransform();
        Vector3d localPosition = new Vector3d(x, y, z);
        renderTransform.transformPosition(localPosition, TransformType.GLOBAL_TO_SUBSPACE);

        minecraft.effectRenderer.spawnEffectParticle(
            particleType.getParticleID(),
            localPosition.x, localPosition.y, localPosition.z,
            0D, ySpeed, 0D,
            parameters
        );
    }
}
