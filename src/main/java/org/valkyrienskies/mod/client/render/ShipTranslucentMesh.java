package org.valkyrienskies.mod.client.render;

import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.lwjgl.opengl.GL11;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;

public class ShipTranslucentMesh {
    @Nullable
    private BufferBuilder.State vertices;
    @Nullable
    private Vector3d sortedCamera;

    public void capture(@NotNull BufferBuilder buffer) {
        this.vertices = buffer.getVertexState();
        this.sortedCamera = null;
    }

    public boolean sort(@NotNull BufferBuilder buffer, @NotNull Vector3dc camera, @NotNull BlockPos offset) {
        if (this.vertices == null || this.vertices.getVertexCount() == 0) return false;
        if (this.sortedCamera != null && this.sortedCamera.distanceSquared(camera) <= 1D) return false;

        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        buffer.setTranslation(0, 0, 0);
        buffer.setVertexState(this.vertices);
        buffer.sortVertexData((float) (camera.x() - offset.getX()),
                (float) (camera.y() - offset.getY()), (float) (camera.z() - offset.getZ()));
        this.sortedCamera = new Vector3d(camera);
        return true;
    }

    @NotNull
    public static Vector3d getCameraPosition(@NotNull Entity camera, double partialTicks, @NotNull ShipTransform transform) {
        Vector3d position = new Vector3d(
                camera.lastTickPosX + (camera.posX - camera.lastTickPosX) * partialTicks,
                camera.lastTickPosY + (camera.posY - camera.lastTickPosY) * partialTicks,
                camera.lastTickPosZ + (camera.posZ - camera.lastTickPosZ) * partialTicks
        );
        return transform.getGlobalToSubspace().transformPosition(position);
    }
}
