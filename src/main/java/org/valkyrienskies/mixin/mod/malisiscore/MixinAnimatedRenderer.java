package org.valkyrienskies.mixin.mod.malisiscore;

import net.malisis.core.block.IComponent;
import net.malisis.core.renderer.IAnimatedRenderable;
import net.malisis.core.renderer.MalisisRenderer;
import net.malisis.core.renderer.component.AnimatedModelComponent;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Coerce;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nullable;
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
        IAnimatedRenderable animatedRenderable = (IAnimatedRenderable) renderable;
        PhysicsObject physicsObject = valkyrienskies$getPhysicsObject(animatedRenderable);
        if (physicsObject == null) return animatedRenderable.inFrustrum(camera);
        return physicsObject.getShipRenderer() != null;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void removeInvalidRenderables(CallbackInfo callbackInfo) {
        animatedRenderables.values().removeIf(rawRenderable -> {
            IAnimatedRenderable renderable = (IAnimatedRenderable) rawRenderable;
            return IComponent.getComponent(
                AnimatedModelComponent.class,
                renderable.getWorld().getBlockState(renderable.getPos()).getBlock()
            ) == null;
        });
    }

    @Inject(method = "renderRenderable", at = @At("HEAD"))
    private void beginShipRender(@Coerce Object rawRenderable, CallbackInfo callbackInfo) {
        IAnimatedRenderable renderable = (IAnimatedRenderable) rawRenderable;
        PhysicsObject physicsObject = valkyrienskies$getPhysicsObject(renderable);
        Entity player = Minecraft.getMinecraft().player;
        Entity viewEntity = Minecraft.getMinecraft().getRenderViewEntity();
        if (physicsObject == null || physicsObject.getShipRenderer() == null || player == null || viewEntity == null) {
            this.valkyrienskies$renderingOnShip = false;
            return;
        }

        Tessellator.getInstance().draw();
        Tessellator.getInstance().getBuffer().begin(GL11.GL_QUADS, MalisisRenderer.malisisVertexFormat);

        float partialTicks = MalisisRenderer.getPartialTick();
        double playerX = player.lastTickPosX + (player.posX - player.lastTickPosX) * partialTicks;
        double playerY = player.lastTickPosY + (player.posY - player.lastTickPosY) * partialTicks;
        double playerZ = player.lastTickPosZ + (player.posZ - player.lastTickPosZ) * partialTicks;

        GlStateManager.pushMatrix();
        GlStateManager.translate(playerX, playerY, playerZ);
        physicsObject.getShipRenderer().applyRenderTransform(partialTicks);
        this.valkyrienskies$renderingOnShip = true;
    }

    @Inject(method = "renderRenderable", at = @At("RETURN"))
    private void endShipRender(@Coerce Object renderable, CallbackInfo callbackInfo) {
        if (!this.valkyrienskies$renderingOnShip) return;
        Tessellator.getInstance().draw();
        GlStateManager.popMatrix();
        Tessellator.getInstance().getBuffer().begin(GL11.GL_QUADS, MalisisRenderer.malisisVertexFormat);
        this.valkyrienskies$renderingOnShip = false;
    }

    @Unique
    @Nullable
    private static PhysicsObject valkyrienskies$getPhysicsObject(IAnimatedRenderable renderable) {
        if (!(renderable.getWorld() instanceof World world)) return null;
        return ValkyrienUtils.getPhysoManagingBlock(world, renderable.getPos()).orElse(null);
    }
}
