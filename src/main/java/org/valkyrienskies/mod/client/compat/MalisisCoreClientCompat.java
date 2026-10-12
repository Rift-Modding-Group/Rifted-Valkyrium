package org.valkyrienskies.mod.client.compat;

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
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.lwjgl.opengl.GL11;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Helper for MalisisCore and by extension MalisisDoors on client
 * */
public class MalisisCoreClientCompat {
    /**
     * Places Malisis block models into Rifted Valkyrium's ship-relative render buffer.
     * */
    @Nonnull
    public static Vec3d getBlockRenderOffset(@Nonnull IBlockAccess blockAccess, @Nonnull BlockPos blockPos, @Nonnull Vec3d originalOffset) {
        if (!(blockAccess instanceof World world)) return originalOffset;

        PhysicsObject physicsObject = ValkyrienUtils.getPhysoManagingBlock(world, blockPos).orElse(null);
        if (physicsObject == null || physicsObject.getShipRenderer() == null) {
            return originalOffset;
        }

        BlockPos rendererOffset = physicsObject.getShipRenderer().offsetPos;
        return new Vec3d(
            blockPos.getX() - rendererOffset.getX(),
            blockPos.getY() - rendererOffset.getY(),
            blockPos.getZ() - rendererOffset.getZ());
    }

    /**
     * Uses the ship's real bounding box when Malisis checks an animated model's visibility.
     * */
    public static boolean shouldRenderAnimated(@Nonnull Object rawRenderable, @Nonnull ICamera camera) {
        IAnimatedRenderable renderable = (IAnimatedRenderable) rawRenderable;
        PhysicsObject physicsObject = getPhysicsObject(renderable);
        if (physicsObject == null) return renderable.inFrustrum(camera);
        return physicsObject.getShipRenderer() != null;
    }

    /**
     * Draws queued models, then applies the ship transform for the next animated model.
     * */
    public static boolean beginAnimatedRender(@Nonnull Object rawRenderable) {
        IAnimatedRenderable renderable = (IAnimatedRenderable) rawRenderable;
        PhysicsObject physicsObject = getPhysicsObject(renderable);
        Entity player = Minecraft.getMinecraft().player;
        Entity viewEntity = Minecraft.getMinecraft().getRenderViewEntity();
        if (physicsObject == null || physicsObject.getShipRenderer() == null || player == null || viewEntity == null) {
            return false;
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
        return true;
    }

    /** Draws the animated model before restoring MalisisCore's normal matrix. */
    public static void endAnimatedRender() {
        Tessellator.getInstance().draw();
        GlStateManager.popMatrix();
        Tessellator.getInstance().getBuffer().begin(GL11.GL_QUADS, MalisisRenderer.malisisVertexFormat);
    }

    /** Drops animated models after their block has been removed. */
    public static boolean isAnimatedRenderableValid(@Nonnull Object rawRenderable) {
        IAnimatedRenderable renderable = (IAnimatedRenderable) rawRenderable;
        return IComponent.getComponent(
            AnimatedModelComponent.class,
            renderable.getWorld().getBlockState(renderable.getPos()).getBlock()
        ) != null;
    }

    @Nullable
    private static PhysicsObject getPhysicsObject(@Nonnull IAnimatedRenderable renderable) {
        if (!(renderable.getWorld() instanceof World world)) return null;
        return ValkyrienUtils.getPhysoManagingBlock(world, renderable.getPos()).orElse(null);
    }
}
