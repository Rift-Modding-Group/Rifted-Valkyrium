package org.valkyrienskies.mixin.mod.xaeroworldmap;

import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.compat.MinimapMod;
import org.valkyrienskies.mod.client.compat.ShipMinimapRenderer;
import org.valkyrienskies.mod.common.config.VSConfig;
import xaero.map.MapProcessor;

@Pseudo
@Mixin(targets = "xaero.map.gui.GuiMap", remap = false)
public abstract class MixinGuiMap {
    @Shadow
    private MapProcessor mapProcessor;
    @Shadow
    private double scale;

    @Inject(
            method = {"drawScreen", "func_73863_a"},
            at = @At(
                    value = "FIELD",
                    target = "Lxaero/map/gui/GuiMap;prevLoadingLeaves:Z",
                    opcode = 181, shift = At.Shift.AFTER
            )
    )
    private void renderShips(
            int mouseX, int mouseY, float partialTicks, CallbackInfo callback,
            @Local(name = "flooredCameraX") int cameraX,
            @Local(name = "flooredCameraZ") int cameraZ,
            @Local(name = "partialTicks") float mapPartialTicks
    ) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!VSConfig.showShipInMinimapMods || mc.world == null || this.mapProcessor.getMapWorld() == null
                || this.mapProcessor.getMapWorld().getCurrentDimension().getDimId() != mc.world.provider.getDimension()
                || this.mapProcessor.getCurrentCaveLayer() != Integer.MAX_VALUE
        ) {
            return;
        }
        ShipMinimapRenderer.INSTANCE.render(
                cameraX, cameraZ, mapPartialTicks,
                Math.max(mc.displayWidth, mc.displayHeight) / this.scale, MinimapMod.XAERO, null
        );
    }
}
