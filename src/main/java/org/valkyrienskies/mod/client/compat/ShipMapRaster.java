package org.valkyrienskies.mod.client.compat;

import net.minecraft.util.math.AxisAlignedBB;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4d;
import org.joml.Matrix4dc;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import valkyrienwarfare.api.TransformType;

import java.util.Arrays;
import java.util.List;

/**
 * Projects block bounds into an X/Z image, keeping the highest face at each pixel.
 * */
public class ShipMapRaster {
    /**
     * A yaw change can rotate the cached top-down image without rebuilding its pixels.
     * */
    public static boolean needsProjection(@NotNull ShipTransform snapshot, @NotNull ShipTransform current) {
        Quaterniond rotation = current.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL)
                .mul(snapshot.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL).invert());
        Vector3d up = rotation.transform(new Vector3d(0D, 1D, 0D));
        return up.y < 0.999999D;
    }

    /**
     * Moves a cached image with its ship's interpolated pose instead of snapping to image updates.
     * */
    public static Vector3d @NotNull [] projectQuad(
            @NotNull Image image, @NotNull ShipTransform snapshot, @NotNull ShipTransform current,
            double cameraX, double cameraZ
    ) {
        double right = image.minX() + image.width() * image.pixelSize();
        double bottom = image.minZ() + image.height() * image.pixelSize();
        Vector3d[] vertices = {
                new Vector3d(image.minX(), 0D, bottom), new Vector3d(right, 0D, bottom),
                new Vector3d(right, 0D, image.minZ()), new Vector3d(image.minX(), 0D, image.minZ())
        };
        Matrix4d snapshotToCurrent = new Matrix4d(current.getSubspaceToGlobal()).mul(snapshot.getGlobalToSubspace());
        for (Vector3d vertex : vertices) {
            vertex.add(snapshot.getPosX(), snapshot.getPosY(), snapshot.getPosZ());
            snapshotToCurrent.transformPosition(vertex);
            vertex.sub(cameraX, 0D, cameraZ);
        }
        return vertices;
    }

    @NotNull
    public static Image rasterize(@NotNull List<MapBlock> blocks, @NotNull Matrix4dc transform, int maxSize) {
        if (blocks.isEmpty()) return new Image(0D, 0D, 1D, 1, 1, new int[1], new double[1]);

        double minX = Double.MAX_VALUE;
        double minZ = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE;
        double maxZ = -Double.MAX_VALUE;
        Vector3d corner = new Vector3d();
        for (MapBlock block : blocks) {
            AxisAlignedBB box = block.bounds();
            for (int i = 0; i < 8; i++) {
                corner.set((i & 1) == 0 ? box.minX : box.maxX, (i & 2) == 0 ? box.minY : box.maxY, (i & 4) == 0 ? box.minZ : box.maxZ);
                transform.transformPosition(corner);
                minX = Math.min(minX, corner.x);
                minZ = Math.min(minZ, corner.z);
                maxX = Math.max(maxX, corner.x);
                maxZ = Math.max(maxZ, corner.z);
            }
        }

        // Two pixels per block on small craft; bounded storage for large ones.
        int edgeLimit = Math.clamp(maxSize, 32, 1024);
        double pixelSize = Math.max(0.5D, Math.max(maxX - minX, maxZ - minZ) / edgeLimit);
        // Rotation roundoff must not add a transparent row or column at right angles.
        int width = Math.clamp((int)Math.ceil((maxX - minX) / pixelSize - 0.00000001D), 1, edgeLimit);
        int height = Math.clamp((int)Math.ceil((maxZ - minZ) / pixelSize - 0.00000001D), 1, edgeLimit);
        int[] colors = new int[width * height];
        double[] heights = new double[colors.length];
        Arrays.fill(heights, -Double.MAX_VALUE);
        Image image = new Image(minX, minZ, pixelSize, width, height, colors, heights);
        Vector3d[] vertices = {new Vector3d(), new Vector3d(), new Vector3d(), new Vector3d()};
        Vector3d normal = new Vector3d();

        for (int axis = 0; axis < 3; axis++) {
            int u = (axis + 1) % 3;
            int v = (axis + 2) % 3;
            for (int sign = -1; sign <= 1; sign += 2) {
                normal.zero().setComponent(axis, sign);
                transform.transformDirection(normal);
                if (normal.y <= 0.000001D) continue;

                for (MapBlock block : blocks) {
                    AxisAlignedBB box = block.bounds();
                    Vector3d lower = new Vector3d(box.minX, box.minY, box.minZ);
                    Vector3d upper = new Vector3d(box.maxX, box.maxY, box.maxZ);
                    for (int i = 0; i < 4; i++) {
                        Vector3d vertex = vertices[i].set(lower);
                        if (sign > 0) vertex.setComponent(axis, upper.get(axis));
                        if (i == 1 || i == 2) vertex.setComponent(u, upper.get(u));
                        if (i >= 2) vertex.setComponent(v, upper.get(v));
                        transform.transformPosition(vertex);
                    }
                    double light = 0.65D + 0.35D * normal.y;
                    int red = (int)((block.color() >> 16 & 255) * light);
                    int green = (int)((block.color() >> 8 & 255) * light);
                    int blue = (int)((block.color() & 255) * light);
                    int color = 0xFF000000 | red << 16 | green << 8 | blue;
                    triangle(image, vertices[0], vertices[1], vertices[2], color);
                    triangle(image, vertices[0], vertices[2], vertices[3], color);
                }
            }
        }

        // Height shading follows the world Z axis even when a craft pitches or rolls.
        for (int z = 1; z < height; z++) {
            for (int x = 0; x < width; x++) {
                int index = z * width + x;
                if (colors[index] == 0 || colors[index - width] == 0) continue;
                double shade = 1D + Math.clamp((heights[index] - heights[index - width]) * 0.12D, -0.2D, 0.2D);
                int red = Math.clamp((int)((colors[index] >> 16 & 255) * shade), 0, 255);
                int green = Math.clamp((int)((colors[index] >> 8 & 255) * shade), 0, 255);
                int blue = Math.clamp((int)((colors[index] & 255) * shade), 0, 255);
                colors[index] = 0xFF000000 | red << 16 | green << 8 | blue;
            }
        }
        return image;
    }

    private static void triangle(@NotNull Image image, @NotNull Vector3d a, @NotNull Vector3d b, @NotNull Vector3d c, int color) {
        double denominator = (b.z - c.z) * (a.x - c.x) + (c.x - b.x) * (a.z - c.z);
        if (Math.abs(denominator) < 0.0000001D) return;
        int minX = Math.clamp((int)Math.floor((Math.min(a.x, Math.min(b.x, c.x)) - image.minX()) / image.pixelSize()), 0, image.width() - 1);
        int maxX = Math.clamp((int)Math.floor((Math.max(a.x, Math.max(b.x, c.x)) - image.minX()) / image.pixelSize()), 0, image.width() - 1);
        int minZ = Math.clamp((int)Math.floor((Math.min(a.z, Math.min(b.z, c.z)) - image.minZ()) / image.pixelSize()), 0, image.height() - 1);
        int maxZ = Math.clamp((int)Math.floor((Math.max(a.z, Math.max(b.z, c.z)) - image.minZ()) / image.pixelSize()), 0, image.height() - 1);
        for (int z = minZ; z <= maxZ; z++) {
            double worldZ = image.minZ() + (z + 0.5D) * image.pixelSize();
            for (int x = minX; x <= maxX; x++) {
                double worldX = image.minX() + (x + 0.5D) * image.pixelSize();
                double wa = ((b.z - c.z) * (worldX - c.x) + (c.x - b.x) * (worldZ - c.z)) / denominator;
                double wb = ((c.z - a.z) * (worldX - c.x) + (a.x - c.x) * (worldZ - c.z)) / denominator;
                double wc = 1D - wa - wb;
                if (wa < -0.000001D || wb < -0.000001D || wc < -0.000001D) continue;
                double y = wa * a.y + wb * b.y + wc * c.y;
                int index = z * image.width() + x;
                if (y < image.heights()[index]) continue;
                image.heights()[index] = y;
                image.colors()[index] = color;
            }
        }
    }

    public record MapBlock(@NotNull AxisAlignedBB bounds, int color) {}

    public record Image(
            double minX, double minZ, double pixelSize, int width, int height,
            int @NotNull [] colors, double @NotNull [] heights
    ) {}
}
