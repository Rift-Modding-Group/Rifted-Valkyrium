package org.valkyrienskies.mixin.client.entity;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.valkyrienskies.mod.client.particle.ShipParticleSpawner;

/**
 * Creates living-entity death particles in the coordinate frame carrying the entity.
 */
@Mixin(EntityLivingBase.class)
public class MixinEntityLivingBaseDeathParticles {
    @WrapOperation(
            method = "onDeathUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;spawnParticle(Lnet/minecraft/util/EnumParticleTypes;DDDDDD[I)V"
            )
    )
    private void spawnDeathParticleOnShip(
            World world,
            EnumParticleTypes particleType,
            double x,
            double y,
            double z,
            double xSpeed,
            double ySpeed,
            double zSpeed,
            int[] parameters,
            Operation<Void> original
    ) {
        EntityLivingBase dyingEntity = (EntityLivingBase) (Object) this;
        if (!ShipParticleSpawner.spawnForEntity(
                dyingEntity,
                particleType,
                x,
                y,
                z,
                xSpeed,
                ySpeed,
                zSpeed,
                parameters
        )) {
            original.call(world, particleType, x, y, z, xSpeed, ySpeed, zSpeed, parameters);
        }
    }
}
