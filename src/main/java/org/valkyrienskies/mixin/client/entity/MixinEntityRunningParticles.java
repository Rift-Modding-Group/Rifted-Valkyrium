package org.valkyrienskies.mixin.client.entity;

import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.client.particle.ShipParticleSpawner;

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
        if (!ShipParticleSpawner.spawnForEntity(entity, particleType,x, y, z, 0D, ySpeed, 0D, parameters)) {
            world.spawnParticle(particleType, x, y, z, xSpeed, ySpeed, zSpeed, parameters);
        }
    }
}
