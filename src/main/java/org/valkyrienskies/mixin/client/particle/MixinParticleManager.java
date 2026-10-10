package org.valkyrienskies.mixin.client.particle;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import javax.annotation.Nullable;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;

/**
 * Keeps particles emitted by ship blocks in ship coordinates while they update, then transforms
 * them with the interpolated ship transform only for rendering.
 */
@Mixin(ParticleManager.class)
public abstract class MixinParticleManager {

    @Unique
    private Map<Particle, PhysicsObject> shipParticles = new WeakHashMap<>();
    @Unique
    private Vector3d renderPosition = new Vector3d();

    @Inject(method = "addEffect", at = @At("HEAD"), cancellable = true)
    public void preAddEffect(Particle effect, CallbackInfo callbackInfoReturnable) {
        if (effect == null) {
            callbackInfoReturnable.cancel();
            return;
        }

        BlockPos pos = new BlockPos(effect.posX, effect.posY, effect.posZ);
        Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(effect.world, pos);
        physicsObject.ifPresent(object -> this.shipParticles.put(effect, object));
    }

    @Inject(method = "clearEffects", at = @At("HEAD"))
    private void clearShipParticles(@Nullable World world, CallbackInfo callbackInfo) {
        this.shipParticles.clear();
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
