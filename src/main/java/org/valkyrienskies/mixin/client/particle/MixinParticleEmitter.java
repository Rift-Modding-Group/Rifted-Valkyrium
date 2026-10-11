package org.valkyrienskies.mixin.client.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.ParticleEmitter;
import net.minecraft.entity.Entity;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.particle.ShipParticleSpawner;

/**
 * Keeps particles emitted around an entity in the coordinate frame carrying that entity.
 */
@Mixin(ParticleEmitter.class)
public class MixinParticleEmitter {
    @Unique
    @Nullable
    private Entity shipParticleSource;

    @Inject(
            method = "<init>(Lnet/minecraft/world/World;Lnet/minecraft/entity/Entity;Lnet/minecraft/util/EnumParticleTypes;I)V",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/ParticleEmitter;onUpdate()V"
            )
    )
    private void rememberShipParticleSource(World world, Entity source, EnumParticleTypes particleType, int lifetime, CallbackInfo callbackInfo) {
        this.shipParticleSource = source;
    }

    @WrapOperation(
            method = "onUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/World;spawnParticle(Lnet/minecraft/util/EnumParticleTypes;ZDDDDDD[I)V"
            )
    )
    private void spawnAttachedParticleOnShip(
            World world, EnumParticleTypes particleType, boolean ignoreRange,
            double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed,
            int[] parameters, Operation<Void> original
    ) {
        if (this.shipParticleSource == null || !ShipParticleSpawner.spawnForEntity(
                this.shipParticleSource, particleType, x, y, z,
                xSpeed, ySpeed, zSpeed, parameters
        )) {
            original.call(world, particleType, ignoreRange, x, y, z, xSpeed, ySpeed, zSpeed, parameters);
        }
    }
}
