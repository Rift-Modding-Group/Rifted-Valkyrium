package org.valkyrienskies.mixin.mod.riftlib;

import net.minecraft.entity.Entity;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3dc;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;

/**
 * Keeps animation queries in the ship frame even while render positions are temporarily replaced.
 * */
@Pseudo
@Mixin(targets = "anightdazingzoroark.riftlib.util.MiscUtils", remap = false)
public abstract class MixinMiscUtils {
    @Inject(method = "getEntityHorizontalSpeed", at = @At("HEAD"), cancellable = true)
    private static void getShipHorizontalSpeed(@NotNull Entity entity, CallbackInfoReturnable<Double> callbackInfo) {
        Vector3dc movement = EntityDraggable.getShipRelativeMovement(entity);
        if (movement == null) return;
        callbackInfo.setReturnValue(Math.hypot(movement.x(), movement.z()));
    }

    @Inject(method = "getEntityVerticalSpeed", at = @At("HEAD"), cancellable = true)
    private static void getShipVerticalSpeed(@NotNull Entity entity, CallbackInfoReturnable<Double> callbackInfo) {
        Vector3dc movement = EntityDraggable.getShipRelativeMovement(entity);
        if (movement == null) return;
        callbackInfo.setReturnValue(movement.y());
    }
}
