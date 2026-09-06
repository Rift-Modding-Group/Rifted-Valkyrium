package org.valkyrienskies.mixin.client.entity;

import net.minecraft.client.entity.EntityOtherPlayerMP;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;

/**
 * Disables the remote-player global-coordinate lerp while ship-local movement owns the entity.
 */
@Mixin(EntityOtherPlayerMP.class)
public class MixinEntityOtherPlayerMPInterpolation {
    @Shadow
    private int otherPlayerMPPosRotationIncrements;

    @Inject(method = "onLivingUpdate", at = @At("HEAD"))
    private void suppressVanillaLerpDuringShipLocalMovement(CallbackInfo callbackInfo) {
        EntityOtherPlayerMP player = (EntityOtherPlayerMP) (Object) this;
        if (EntityDraggable.isUsingShipLocalMovement(player)) {
            this.otherPlayerMPPosRotationIncrements = 0;
        }
    }
}
