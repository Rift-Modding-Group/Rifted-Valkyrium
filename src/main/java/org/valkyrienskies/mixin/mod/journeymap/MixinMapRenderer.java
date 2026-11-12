package org.valkyrienskies.mixin.mod.journeymap;

import journeymap.client.model.map.MapType;
import journeymap.client.render.RegionTileShaders;
import journeymap.client.render.backend.LayerSink;
import journeymap.client.render.backend.RenderBackends;
import journeymap.client.render.backend.RenderContext;
import journeymap.client.render.backend.RenderPose;
import journeymap.client.render.map.MapRenderer;
import net.minecraft.client.Minecraft;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.compat.MinimapMod;
import org.valkyrienskies.mod.client.compat.ShipMinimapRenderer;
import org.valkyrienskies.mod.common.config.VSConfig;

@Pseudo
@Mixin(targets = "journeymap.client.render.map.MapRenderer", remap = false)
public abstract class MixinMapRenderer {
    @Shadow
    private static boolean enabled;

    @Inject(method = "render", at = @At("TAIL"), require = 1)
    private void renderShips(
            @NotNull RenderContext ctx, double offsetX, double offsetZ, float alpha, boolean showGrid,
            @NotNull CallbackInfo callback
    ) {
        Minecraft mc = Minecraft.getMinecraft();
        MapRenderer renderer = (MapRenderer) (Object) this;
        MapType mapType = renderer.getMapType();
        if (!enabled || !VSConfig.showShipInMinimapMods || mc.world == null || mapType == null
                || mapType.dimension != mc.world.provider.getDimension()
        ) {
            return;
        }

        double cameraX = renderer.getCenterBlockX();
        double cameraZ = renderer.getCenterBlockZ();
        double blockSize = renderer.getZoom() / 512D;
        double radius = (Math.hypot(mc.displayWidth, mc.displayHeight) / 2D + Math.hypot(offsetX, offsetZ)) / blockSize;
        float partialTicks = mc.isGamePaused() ? 1f : mc.getRenderPartialTicks();
        String filter = RegionTileShaders.from(mapType.getShader());
        double originX = mc.displayWidth / 2D + offsetX;
        double originY = mc.displayHeight / 2D + offsetZ;
        double maxVisibleY = Double.MAX_VALUE;
        if (mapType.isUnderground() && mapType.vSlice != null) {
            maxVisibleY = (mapType.vSlice + 1D) * 16D;
        }

        // Flush terrain before the ships. Both maps draw their markers afterwards;
        // the minimap's depth mask and rotation are still active here.
        ctx.flushLayers();
        ShipMinimapRenderer.INSTANCE.render(
                cameraX, cameraZ, partialTicks, radius, mapType.isUnderground(), maxVisibleY, MinimapMod.JOURNEYMAP,
                new MinimapMod.JourneyMapContext((texture, vertices) -> {
                    LayerSink sink = ctx.layer(RenderBackends.get().layers().mapTile(texture.getGlTextureId(), filter));
                    RenderPose pose = ctx.currentPose();
                    for (int i = 0; i < vertices.length; i++) {
                        Vector3d vertex = vertices[i];
                        float u = i == 1 || i == 2 ? 1f : 0f;
                        float v = i < 2 ? 1F : 0F;
                        sink.pos(pose, originX + vertex.x * blockSize, originY + vertex.z * blockSize, 0D)
                                .uv(u, v).color(1f, 1f, 1f, alpha).endVertex();
                    }
                })
        );
        ctx.flushLayers();
    }
}
