package org.valkyrienskies.mixin.client.renderer;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.renderer.RenderGlobal;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.client.particle.ShipParticleSpawner;
import org.valkyrienskies.mod.client.particle.ShipParticleSpawner.SpawnResult;

/**
 * Resolves ship ownership before vanilla rejects ship-space coordinates as out of range.
 * */
@Mixin(RenderGlobal.class)
public class MixinRenderGlobalParticles {
    @Inject(
            method = "spawnParticle0(IZZDDDDDD[I)Lnet/minecraft/client/particle/Particle;",
            at = @At("HEAD"),
            cancellable = true
    )
    private void spawnParticleInShipFrame(
            int particleId, boolean ignoreRange, boolean minimiseParticleLevel,
            double x, double y, double z, double xSpeed, double ySpeed, double zSpeed,
            int[] parameters, CallbackInfoReturnable<Particle> callbackInfo
    ) {
        WorldClient world = Minecraft.getMinecraft().world;
        if (world == null) return;

        SpawnResult result = ShipParticleSpawner.spawnIfShip(
                world, particleId, ignoreRange, minimiseParticleLevel,
                x, y, z, xSpeed, ySpeed, zSpeed, parameters
        );
        if (result.handled()) callbackInfo.setReturnValue(result.particle());
    }
}
