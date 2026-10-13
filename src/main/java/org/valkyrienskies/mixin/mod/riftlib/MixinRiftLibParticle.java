package org.valkyrienskies.mixin.mod.riftlib;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.ShipParticleResolver;
import org.valkyrienskies.mod.common.ships.ship_world.ShipParticleResolver.ResolvedShip;
import valkyrienwarfare.api.TransformType;

import javax.annotation.Nullable;

/**
 * For making sure RiftLibrary particles stay in ship space when
 * their source is on a ship.
 */
@Pseudo
@Mixin(targets = "anightdazingzoroark.riftlib.particle.RiftLibParticle", remap = false)
public abstract class MixinRiftLibParticle {
    @Shadow
    @Final
    @Nullable
    private World world;

    @Shadow
    private double x;

    @Shadow
    private double y;

    @Shadow
    private double z;

    @Shadow
    private double prevX;

    @Shadow
    private double prevY;

    @Shadow
    private double prevZ;

    @Shadow
    private double velX;

    @Shadow
    private double velY;

    @Shadow
    private double velZ;

    @Shadow
    private boolean useLocalLighting;

    @Unique
    @Nullable
    private PhysicsObject valkyrienskies$ship;

    @Unique
    private boolean valkyrienskies$transformInitialVelocity;

    @Unique
    private boolean valkyrienskies$renderingInWorld;

    @Unique
    private double valkyrienskies$localX;

    @Unique
    private double valkyrienskies$localY;

    @Unique
    private double valkyrienskies$localZ;

    @Unique
    private double valkyrienskies$localPrevX;

    @Unique
    private double valkyrienskies$localPrevY;

    @Unique
    private double valkyrienskies$localPrevZ;

    @Unique
    private Vector3d valkyrienskies$transformed = new Vector3d();

    @Inject(method = "setPosition", at = @At("RETURN"))
    private void moveInitialPositionIntoShip(double x, double y, double z, CallbackInfo callbackInfo) {
        this.valkyrienskies$ship = null;
        this.valkyrienskies$transformInitialVelocity = false;
        if (this.world == null) return;

        ResolvedShip resolvedShip = ShipParticleResolver.resolve(this.world, x, y, z);
        if (resolvedShip == null) return;

        this.valkyrienskies$ship = resolvedShip.ship();
        if (resolvedShip.localCoordinates()) return;

        ShipTransform renderTransform = resolvedShip.ship().getShipTransformationManager().getRenderTransform();
        renderTransform.transformPosition(
            this.valkyrienskies$transformed.set(x, y, z),
            TransformType.GLOBAL_TO_SUBSPACE
        );
        this.x = this.prevX = this.valkyrienskies$transformed.x;
        this.y = this.prevY = this.valkyrienskies$transformed.y;
        this.z = this.prevZ = this.valkyrienskies$transformed.z;
        this.valkyrienskies$transformInitialVelocity = true;
    }

    @Inject(method = "initializeVelocity", at = @At("RETURN"))
    private void moveInitialVelocityIntoShip(Vec3d direction, CallbackInfo callbackInfo) {
        PhysicsObject ship = this.valkyrienskies$ship;
        if (ship == null || !this.valkyrienskies$transformInitialVelocity) return;

        ship.getShipTransformationManager().getRenderTransform().transformDirection(
            this.valkyrienskies$transformed.set(this.velX, this.velY, this.velZ),
            TransformType.GLOBAL_TO_SUBSPACE
        );
        this.velX = this.valkyrienskies$transformed.x;
        this.velY = this.valkyrienskies$transformed.y;
        this.velZ = this.valkyrienskies$transformed.z;
    }

    @Inject(method = "renderParticle", at = @At("HEAD"))
    private void moveRenderPositionIntoWorld(BufferBuilder buffer, Entity cameraEntity, float partialTicks, CallbackInfo callbackInfo) {
        PhysicsObject ship = this.valkyrienskies$ship;
        if (ship == null) return;

        this.valkyrienskies$localX = this.x;
        this.valkyrienskies$localY = this.y;
        this.valkyrienskies$localZ = this.z;
        this.valkyrienskies$localPrevX = this.prevX;
        this.valkyrienskies$localPrevY = this.prevY;
        this.valkyrienskies$localPrevZ = this.prevZ;

        ShipTransform renderTransform = ship.getShipTransformationManager().getRenderTransform();
        renderTransform.transformPosition(
            this.valkyrienskies$transformed.set(this.prevX, this.prevY, this.prevZ),
            TransformType.SUBSPACE_TO_GLOBAL
        );
        this.prevX = this.valkyrienskies$transformed.x;
        this.prevY = this.valkyrienskies$transformed.y;
        this.prevZ = this.valkyrienskies$transformed.z;

        renderTransform.transformPosition(
            this.valkyrienskies$transformed.set(this.x, this.y, this.z),
            TransformType.SUBSPACE_TO_GLOBAL
        );
        this.x = this.valkyrienskies$transformed.x;
        this.y = this.valkyrienskies$transformed.y;
        this.z = this.valkyrienskies$transformed.z;
        this.valkyrienskies$renderingInWorld = true;
    }

    @Inject(method = "renderParticle", at = @At("RETURN"))
    private void restoreShipLocalPosition(BufferBuilder buffer, Entity cameraEntity, float partialTicks, CallbackInfo callbackInfo) {
        if (!this.valkyrienskies$renderingInWorld) return;

        this.x = this.valkyrienskies$localX;
        this.y = this.valkyrienskies$localY;
        this.z = this.valkyrienskies$localZ;
        this.prevX = this.valkyrienskies$localPrevX;
        this.prevY = this.valkyrienskies$localPrevY;
        this.prevZ = this.valkyrienskies$localPrevZ;
        this.valkyrienskies$renderingInWorld = false;
    }

    @Inject(method = "getBrightnessForRender", at = @At("HEAD"), cancellable = true)
    private void useShipLocalLighting(float partialTicks, CallbackInfoReturnable<Integer> callbackInfo) {
        if (!this.valkyrienskies$renderingInWorld || !this.useLocalLighting || this.world == null) return;

        double lightX = this.valkyrienskies$localPrevX + (this.valkyrienskies$localX - this.valkyrienskies$localPrevX) * partialTicks;
        double lightY = this.valkyrienskies$localPrevY + (this.valkyrienskies$localY - this.valkyrienskies$localPrevY) * partialTicks;
        double lightZ = this.valkyrienskies$localPrevZ + (this.valkyrienskies$localZ - this.valkyrienskies$localPrevZ) * partialTicks;
        BlockPos lightPos = new BlockPos(lightX, lightY, lightZ);
        int light = this.world.isBlockLoaded(lightPos) ? this.world.getCombinedLight(lightPos, 0) : 0;
        callbackInfo.setReturnValue(light);
    }
}
