package org.valkyrienskies.mixin.client.entity;

import net.minecraft.entity.item.EntityMinecart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;

/**
 * Prevents a stale vanilla world-space target from moving a minecart before ship-local interpolation runs.
 */
@Mixin(EntityMinecart.class)
public class MixinEntityMinecartInterpolation {
    @Shadow
    private int turnProgress;

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void suppressVanillaLerpDuringShipLocalMovement(CallbackInfo callbackInfo) {
        EntityMinecart entity = (EntityMinecart) (Object) this;
        if (EntityDraggable.isUsingShipLocalMovement(entity)) {
            this.turnProgress = 0;
        }
    }
}
