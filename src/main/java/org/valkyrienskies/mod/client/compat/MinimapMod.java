package org.valkyrienskies.mod.client.compat;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/**
 * Helper enum that defines how ships are to be drawn on each minimap mod
 * */
public enum MinimapMod {
    XAERO(
            () -> {
                int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
                GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_TEXTURE_BIT | GL11.GL_CURRENT_BIT);
                GL11.glEnable(GL11.GL_TEXTURE_2D);
                GL11.glEnable(GL11.GL_BLEND);
                GL11.glDisable(GL11.GL_ALPHA_TEST);
                GL11.glDisable(GL11.GL_DEPTH_TEST);
                GL11.glDisable(GL11.GL_CULL_FACE);
                GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
                GL11.glColor4f(1f, 1f, 1f, 1f);
                return boundTexture;
            },
            context -> (texture, vertices) -> {
                GlStateManager.bindTexture(texture.getGlTextureId());
                Tessellator tessellator = Tessellator.getInstance();
                BufferBuilder buffer = tessellator.getBuffer();
                buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);
                buffer.pos(vertices[0].x, vertices[0].z, 0D).tex(0D, 1D).endVertex();
                buffer.pos(vertices[1].x, vertices[1].z, 0D).tex(1D, 1D).endVertex();
                buffer.pos(vertices[2].x, vertices[2].z, 0D).tex(1D, 0D).endVertex();
                buffer.pos(vertices[3].x, vertices[3].z, 0D).tex(0D, 0D).endVertex();
                tessellator.draw();
            },
            boundTexture -> {
                GlStateManager.bindTexture(boundTexture);
                GL11.glPopAttrib();
            }
    ),
    JOURNEYMAP(
            () -> 0,
            context -> Objects.requireNonNull(context, "JourneyMap requires its render context").drawing(),
            boundTexture -> {}
    );

    @NotNull
    private final IntSupplier setup;
    @NotNull
    private final Function<JourneyMapContext, BiConsumer<DynamicTexture, Vector3d[]>> drawing;
    @NotNull
    private final IntConsumer cleanup;

    MinimapMod(
            @NotNull IntSupplier setup,
            @NotNull Function<JourneyMapContext, BiConsumer<DynamicTexture, Vector3d[]>> drawing,
            @NotNull IntConsumer cleanup
    ) {
        this.setup = setup;
        this.drawing = drawing;
        this.cleanup = cleanup;
    }

    @NotNull
    public IntSupplier getSetup() {
        return this.setup;
    }

    @NotNull
    public Function<JourneyMapContext, BiConsumer<DynamicTexture, Vector3d[]>> getDrawing() {
        return this.drawing;
    }

    @NotNull
    public IntConsumer getCleanup() {
        return this.cleanup;
    }

    //special param for journeymap
    public record JourneyMapContext(
            @NotNull BiConsumer<DynamicTexture, Vector3d[]> drawing
    ) {}
}
