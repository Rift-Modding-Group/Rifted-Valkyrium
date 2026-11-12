package org.valkyrienskies.mixin.mod.fluidlogged_api;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.function.BiConsumer;

@Pseudo
@Mixin(targets = "git.jbredwards.fluidlogged_api.api.network.MessageUtils", remap = false)
public abstract class MixinMessageUtils {
    /**
     * Routes live fluid updates through the ship's watching players.
     */
    @Inject(
        method = "sendToAllTracking(Lnet/minecraftforge/fml/common/network/simpleimpl/IMessage;Lnet/minecraft/world/chunk/Chunk;Ljava/util/function/BiConsumer;)V",
        at = @At("HEAD"),
        cancellable = true,
        remap = false
    )
    private static void sendToShipWatchers(IMessage message, Chunk chunk, BiConsumer<IMessage, EntityPlayerMP> networkWrapper, CallbackInfo callback) {
        if (chunk.getWorld().isRemote) return;
        PhysicsObject ship = ValkyrienUtils.getPhysoManagingBlock(chunk.getWorld(), new BlockPos(chunk.x << 4, 0, chunk.z << 4)).orElse(null);
        if (ship == null) return;

        for (EntityPlayerMP player : ship.getWatchingPlayers()) networkWrapper.accept(message, player);
        callback.cancel();
    }
}
