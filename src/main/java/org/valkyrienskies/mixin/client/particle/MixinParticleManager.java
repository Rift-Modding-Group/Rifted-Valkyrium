package org.valkyrienskies.mixin.client.particle;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.particle.IParticleFactory;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.particle.ShipParticleSpawner;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.ShipParticleResolver;
import org.valkyrienskies.mod.common.ships.ship_world.ShipParticleResolver.ResolvedShip;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Assigns every particle created in or around a ship to that ship's local coordinate frame, then
 * transforms it back to the interpolated world position only while rendering.
 */
@Mixin(ParticleManager.class)
public abstract class MixinParticleManager {

    @Unique
    private Map<Particle, PhysicsObject> shipParticles = new WeakHashMap<>();
    @Unique
    private Vector3d renderPosition = new Vector3d();

    @WrapOperation(
            method = "spawnEffectParticle",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/client/particle/IParticleFactory;createParticle(ILnet/minecraft/world/World;DDDDDD[I)Lnet/minecraft/client/particle/Particle;"
            )
    )
    @Nullable
    private Particle createParticleInShipFrame(
            IParticleFactory factory, int particleId, World world,
            double x, double y, double z,
            double xSpeed, double ySpeed, double zSpeed,
            int[] parameters, Operation<Particle> original
    ) {
        ResolvedShip resolvedShip = ShipParticleResolver.resolve(world, x, y, z);
        if (resolvedShip == null) {
            return original.call(factory, particleId, world, x, y, z, xSpeed, ySpeed, zSpeed, parameters);
        }

        Vector3d position = new Vector3d(x, y, z);
        Vector3d velocity = new Vector3d(xSpeed, ySpeed, zSpeed);
        if (!resolvedShip.localCoordinates()) {
            ShipParticleSpawner.transformToLocal(resolvedShip.ship(), particleId, position, velocity);
        }

        Particle particle = original.call(
                factory, particleId, world,
                position.x, position.y, position.z,
                velocity.x, velocity.y, velocity.z,
                parameters
        );
        if (particle != null) this.shipParticles.put(particle, resolvedShip.ship());
        return particle;
    }

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    public void preAddEffect(Particle effect, CallbackInfo callbackInfoReturnable) {
        if (effect == null) {
            callbackInfoReturnable.cancel();
            return;
        }
        if (this.shipParticles.containsKey(effect)) return;

        BlockPos pos = new BlockPos(effect.posX, effect.posY, effect.posZ);
        Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(effect.world, pos);
        if (physicsObject.isPresent()) {
            this.shipParticles.put(effect, physicsObject.get());
            return;
        }

        PhysicsObject worldShip = ShipParticleResolver.findWorldShip(effect.world, effect.posX, effect.posY, effect.posZ);
        if (worldShip == null) return;

        ShipTransform renderTransform = worldShip.getShipTransformationManager().getRenderTransform();
        Vector3d position = new Vector3d(effect.posX, effect.posY, effect.posZ);
        Vector3d previousPosition = new Vector3d(effect.prevPosX, effect.prevPosY, effect.prevPosZ);
        Vector3d velocity = new Vector3d(effect.motionX, effect.motionY, effect.motionZ);
        renderTransform.transformPosition(position, TransformType.GLOBAL_TO_SUBSPACE);
        renderTransform.transformPosition(previousPosition, TransformType.GLOBAL_TO_SUBSPACE);
        renderTransform.transformDirection(velocity, TransformType.GLOBAL_TO_SUBSPACE);
        effect.setPosition(position.x, position.y, position.z);
        effect.prevPosX = previousPosition.x;
        effect.prevPosY = previousPosition.y;
        effect.prevPosZ = previousPosition.z;
        effect.motionX = velocity.x;
        effect.motionY = velocity.y;
        effect.motionZ = velocity.z;
        this.shipParticles.put(effect, worldShip);
    }

    @Inject(method = "clearEffects", at = @At("HEAD"))
    private void clearShipParticles(@Nullable World world, CallbackInfo callbackInfo) {
        this.shipParticles.clear();
    }

    /**
     * Layer-three particles render before vanilla refreshes the shared particle camera position.
     * Refresh it here so entity particles, including item pickup effects, use this render frame.
     */
    @Inject(method = "renderLitParticles", at = @At("HEAD"))
    private void updateLitParticleCameraPosition(Entity camera, float partialTicks, CallbackInfo callbackInfo) {
        Particle.interpPosX = camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks;
        Particle.interpPosY = camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks;
        Particle.interpPosZ = camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks;
    }

    @Redirect(
        method = {"renderParticles", "renderLitParticles"},
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/client/particle/Particle;renderParticle(Lnet/minecraft/client/renderer/BufferBuilder;Lnet/minecraft/entity/Entity;FFFFFF)V"
        )
    )
    private void renderParticleInWorld(
            Particle particle, BufferBuilder buffer, Entity camera, float partialTicks,
            float rotationX, float rotationZ, float rotationYZ, float rotationXY, float rotationXZ
    ) {
        PhysicsObject ship = this.shipParticles.get(particle);
        if (ship == null) {
            particle.renderParticle(buffer, camera, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);
            return;
        }

        double localPrevPosX = particle.prevPosX;
        double localPrevPosY = particle.prevPosY;
        double localPrevPosZ = particle.prevPosZ;
        double localPosX = particle.posX;
        double localPosY = particle.posY;
        double localPosZ = particle.posZ;
        ShipTransform renderTransform = ship.getShipTransformationManager().getRenderTransform();
        renderTransform.transformPosition(
            this.renderPosition.set(localPrevPosX, localPrevPosY, localPrevPosZ),
            TransformType.SUBSPACE_TO_GLOBAL
        );
        particle.prevPosX = this.renderPosition.x;
        particle.prevPosY = this.renderPosition.y;
        particle.prevPosZ = this.renderPosition.z;

        renderTransform.transformPosition(
            this.renderPosition.set(localPosX, localPosY, localPosZ),
            TransformType.SUBSPACE_TO_GLOBAL
        );
        particle.posX = this.renderPosition.x;
        particle.posY = this.renderPosition.y;
        particle.posZ = this.renderPosition.z;
        particle.renderParticle(buffer, camera, partialTicks, rotationX, rotationZ, rotationYZ, rotationXY, rotationXZ);

        particle.prevPosX = localPrevPosX;
        particle.prevPosY = localPrevPosY;
        particle.prevPosZ = localPrevPosZ;
        particle.posX = localPosX;
        particle.posY = localPosY;
        particle.posZ = localPosZ;
    }
}
