package org.valkyrienskies.mixin.mod.malisiscore;

import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.compat.MalisisCoreClientCompat;

import java.util.Map;

/**
 * Makes MalisisCore's animated world-last models follow their ship.
 * Mostly to fix the hi-tech door from Malisis doors on ships.
 */
@Pseudo
@Mixin(targets = "net.malisis.core.renderer.AnimatedRenderer", remap = false)
public abstract class MixinAnimatedRenderer {
    @Shadow
    private static Map<BlockPos, Object> animatedRenderables;

    @Unique
    private boolean valkyrienskies$renderingOnShip;

    @Redirect(
        method = "lambda$render$0",
        at = @At(
            value = "INVOKE",
            target = "Lnet/malisis/core/renderer/IAnimatedRenderable;inFrustrum(Lnet/minecraft/client/renderer/culling/ICamera;)Z"
        )
    )
    private static boolean useShipRenderBounds(@Coerce Object renderable, ICamera camera) {
        return MalisisCoreClientCompat.shouldRenderAnimated(renderable, camera);
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void removeInvalidRenderables(CallbackInfo callbackInfo) {
        animatedRenderables.values().removeIf(renderable -> !MalisisCoreClientCompat.isAnimatedRenderableValid(renderable));
    }

    @Inject(method = "renderRenderable", at = @At("HEAD"))
    private void beginShipRender(@Coerce Object renderable, CallbackInfo callbackInfo) {
        this.valkyrienskies$renderingOnShip = MalisisCoreClientCompat.beginAnimatedRender(renderable);
    }

    @Inject(method = "renderRenderable", at = @At("RETURN"))
    private void endShipRender(@Coerce Object renderable, CallbackInfo callbackInfo) {
        if (!this.valkyrienskies$renderingOnShip) return;
        MalisisCoreClientCompat.endAnimatedRender();
        this.valkyrienskies$renderingOnShip = false;
    }
}
