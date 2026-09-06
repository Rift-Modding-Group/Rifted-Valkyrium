package org.valkyrienskies.mixin.client.entity;

import net.minecraft.entity.item.EntityBoat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;

/**
 * Prevents a stale vanilla world-space target from moving a boat before ship-local interpolation runs.
 */
@Mixin(EntityBoat.class)
public class MixinEntityBoatInterpolation {
    @Shadow
    private int lerpSteps;

    @Inject(method = "tickLerp", at = @At("HEAD"), cancellable = true)
    private void suppressVanillaLerpDuringShipLocalMovement(CallbackInfo callbackInfo) {
        EntityBoat entity = (EntityBoat) (Object) this;
        if (!EntityDraggable.isUsingShipLocalMovement(entity)) return;

        this.lerpSteps = 0;
        callbackInfo.cancel();
    }
}
