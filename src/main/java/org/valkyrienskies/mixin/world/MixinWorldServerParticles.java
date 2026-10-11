package org.valkyrienskies.mixin.world;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.EnumParticleTypes;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.network.MessageShipParticles;
import org.valkyrienskies.mod.common.ships.ship_world.ShipParticleResolver;
import org.valkyrienskies.mod.common.ships.ship_world.ShipParticleResolver.ResolvedShip;
import valkyrienwarfare.api.TransformType;

/**
 * Replaces coordinate-only server particle packets with stable ship-local particle data.
 * */
@Mixin(WorldServer.class)
public class MixinWorldServerParticles {
    @Inject(
            method = "spawnParticle(Lnet/minecraft/util/EnumParticleTypes;ZDDDIDDDD[I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sendShipParticles(
            EnumParticleTypes particleType, boolean longDistance,
            double x, double y, double z, int count,
            double xOffset, double yOffset, double zOffset,
            double particleSpeed, int[] parameters, CallbackInfo callbackInfo
    ) {
        WorldServer world = (WorldServer) (Object) this;
        ResolvedShip resolvedShip = ShipParticleResolver.resolve(world, x, y, z);
        if (resolvedShip == null) return;

        Vector3d worldPosition = getWorldPosition(resolvedShip, x, y, z);
        double range = longDistance ? 512D : 32D;
        NetworkRegistry.TargetPoint target = new NetworkRegistry.TargetPoint(
                world.provider.getDimension(),
                worldPosition.x,
                worldPosition.y,
                worldPosition.z,
                range
        );
        ValkyrienSkiesMod.physWrapperNetwork.sendToAllAround(
                new MessageShipParticles(
                        resolvedShip, particleType, longDistance,
                        x, y, z, count, xOffset, yOffset, zOffset,
                        particleSpeed, parameters
                ),
                target
        );
        callbackInfo.cancel();
    }

    @Inject(
            method = "spawnParticle(Lnet/minecraft/entity/player/EntityPlayerMP;Lnet/minecraft/util/EnumParticleTypes;ZDDDIDDDD[I)V",
            at = @At("HEAD"),
            cancellable = true
    )
    private void sendShipParticlesToPlayer(
            EntityPlayerMP player, EnumParticleTypes particleType, boolean longDistance,
            double x, double y, double z, int count, double xOffset, double yOffset, double zOffset,
            double particleSpeed, int[] parameters, CallbackInfo callbackInfo
    ) {
        WorldServer world = (WorldServer) (Object) this;
        ResolvedShip resolvedShip = ShipParticleResolver.resolve(world, x, y, z);
        if (resolvedShip == null) return;

        Vector3d worldPosition = getWorldPosition(resolvedShip, x, y, z);
        BlockPos playerPosition = player.getPosition();
        double distanceSquared = playerPosition.distanceSq(worldPosition.x, worldPosition.y, worldPosition.z);
        double maximumDistanceSquared = longDistance ? 262144D : 1024D;
        if (distanceSquared <= maximumDistanceSquared) {
            ValkyrienSkiesMod.physWrapperNetwork.sendTo(
                    new MessageShipParticles(
                            resolvedShip, particleType, longDistance,
                            x, y, z, count, xOffset, yOffset, zOffset,
                            particleSpeed, parameters
                    ),
                    player
            );
        }
        callbackInfo.cancel();
    }

    @NotNull
    private static Vector3d getWorldPosition(@NotNull ResolvedShip resolvedShip, double x, double y, double z) {
        Vector3d worldPosition = new Vector3d(x, y, z);
        if (resolvedShip.localCoordinates()) {
            resolvedShip.ship().getShipTransformationManager().getCurrentTickTransform()
                    .transformPosition(worldPosition, TransformType.SUBSPACE_TO_GLOBAL);
        }
        return worldPosition;
    }
}
