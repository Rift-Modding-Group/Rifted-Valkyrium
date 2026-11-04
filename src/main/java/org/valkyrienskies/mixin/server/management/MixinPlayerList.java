package org.valkyrienskies.mixin.server.management;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import javax.annotation.Nullable;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.network.Packet;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.valkyrienskies.mod.common.network.VSNetwork;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.network.MessageShipBedRespawn;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.Optional;

/**
 * As much as I don't like it, this mixin is absolutely necessary.
 */
@Mixin(PlayerList.class)
public abstract class MixinPlayerList {
    @WrapOperation(
            method = "recreatePlayerEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetHandlerPlayServer;setPlayerLocation(DDDFF)V")
    )
    private void sendShipSpawnBeforeTeleport(
            @NotNull NetHandlerPlayServer connection, double x, double y, double z, float yaw, float pitch,
            @NotNull Operation<Void> original, @Local(ordinal = 1) @NotNull EntityPlayerMP respawnedPlayer
    ) {
        IEntityShipDraggable draggable = respawnedPlayer.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        ShipData ship = draggable == null ? null : draggable.getLastTouchedShip();
        if (ship != null) {
            MessageShipBedRespawn message = new MessageShipBedRespawn(
                    respawnedPlayer.dimension, ship.getUuid(), new Vector3d(x, y, z), ship.getShipTransform()
            );
            ValkyrienSkiesMod.physWrapperNetwork.sendTo(message, respawnedPlayer);
        }
        original.call(connection, x, y, z, yaw, pitch);
    }

    /**
     * Resolve the bed's ship coordinates before vanilla checks collisions, tracks chunks, or
     * sends the respawn teleport. The connection still refers to the old player at this point.
     */
    @Redirect(
            method = "recreatePlayerEntity",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/EntityPlayerMP;setLocationAndAngles(DDDFF)V")
    )
    private void placeRespawnedPlayer(@NotNull EntityPlayerMP player, double x, double y, double z, float yaw, float pitch) {
        // ShipData remains available even when the bed's ship is too far away to be loaded.
        Optional<ShipData> ship = ValkyrienUtils.getShipManagingBlock(player.world, new BlockPos(x, y, z));
        if (ship.isPresent()) {
            ShipData spawningShip = ship.get();
            Vector3d position = new Vector3d(x, y, z);
            spawningShip.getShipTransform().transformPosition(position, TransformType.SUBSPACE_TO_GLOBAL);
            player.setLocationAndAngles(position.x, position.y, position.z, yaw, pitch);
            EntityDraggable.startTrackingShip(player, spawningShip);
        }
        else {
            player.setLocationAndAngles(x, y, z, yaw, pitch);
        }
    }

    /**
     * Idk maybe Ill replace this with a pre injector. Does this even have an srg mapping?
     *
     * @author thebest108
     */
    @Overwrite
    public void sendToAllNearExcept(@Nullable EntityPlayer except, double x, double y, double z, double radius, int dimension, Packet<?> packetIn) {
        VSNetwork.sendToAllNearExcept(except, x, y, z, radius, dimension, packetIn);
    }
}
