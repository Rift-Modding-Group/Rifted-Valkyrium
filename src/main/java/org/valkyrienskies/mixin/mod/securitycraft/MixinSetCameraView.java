package org.valkyrienskies.mixin.mod.securitycraft;

import net.minecraft.entity.Entity;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "net.geforcemods.securitycraft.network.client.SetCameraView$Handler", remap = false)
public abstract class MixinSetCameraView {
    //let the world preserve or transfer the player's existing chunk membership without ticking it
    @Redirect(
            method = "lambda$onMessage$0",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/chunk/Chunk;addEntity(Lnet/minecraft/entity/Entity;)V",
                    remap = true
            )
    )
    private static void synchronizePlayerChunk(@NotNull Chunk chunk, @NotNull Entity player) {
        chunk.getWorld().updateEntityWithOptionalForce(player, false);
    }
}
