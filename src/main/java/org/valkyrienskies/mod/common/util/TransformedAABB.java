package org.valkyrienskies.mod.common.util;

import net.minecraft.util.math.AxisAlignedBB;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import valkyrienwarfare.api.TransformType;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility representation of an AABB after an optional transform. This is not a physics collider;
 * PhysX owns ship/world/entity collision. It remains for rendering bounds and player-only vanilla
 * movement support.
 */
public class TransformedAABB {
    private final Vector3dc[] vertices;
    private AxisAlignedBB enclosedBBCache;

    public TransformedAABB(@Nonnull AxisAlignedBB bb, @Nullable ShipTransform transformation, @Nullable TransformType transformType) {
        Vector3d[] verticesMutable = getCornersForAABB(bb);
        if (transformation != null && transformType != null) {
            transform(verticesMutable, transformation, transformType);
        }
        this.vertices = verticesMutable;
        this.enclosedBBCache = null;
    }

    public TransformedAABB(@Nonnull AxisAlignedBB bb) {
        this(bb, null, null);
    }

    public TransformedAABB(@Nonnull AxisAlignedBB aabb, @Nonnull Matrix4dc transform) {
        Vector3d[] verticesMutable = getCornersForAABB(aabb);
        transform(verticesMutable, transform);
        this.vertices = verticesMutable;
        this.enclosedBBCache = null;
    }

    public static Vector3d[] generateAxisAlignedNorms() {
        return new Vector3d[]{
            new Vector3d(1.0D, 0.0D, 0.0D),
            new Vector3d(0.0D, 1.0D, 0.0D),
            new Vector3d(0.0D, 0.0D, 1.0D)
        };
    }

    private static Vector3d[] getCornersForAABB(AxisAlignedBB bb) {
        return new Vector3d[]{
            new Vector3d(bb.minX, bb.minY, bb.minZ),
            new Vector3d(bb.minX, bb.maxY, bb.minZ),
            new Vector3d(bb.minX, bb.minY, bb.maxZ),
            new Vector3d(bb.minX, bb.maxY, bb.maxZ),
            new Vector3d(bb.maxX, bb.minY, bb.minZ),
            new Vector3d(bb.maxX, bb.maxY, bb.minZ),
            new Vector3d(bb.maxX, bb.minY, bb.maxZ),
            new Vector3d(bb.maxX, bb.maxY, bb.maxZ)
        };
    }

    public double[] getProjectionOnVector(Vector3dc axis) {
        double[] distances = new double[vertices.length];
        for (int i = 0; i < vertices.length; i++) {
            distances[i] = axis.dot(vertices[i]);
        }
        return distances;
    }

    public Vector3d getCenter() {
        Vector3d center = new Vector3d();
        for (Vector3dc vertex : vertices) {
            center.add(vertex);
        }
        center.mul(1.0D / vertices.length);
        return center;
    }

    private static void transform(Vector3d[] vertices, ShipTransform transformation, TransformType transformType) {
        for (Vector3d vertex : vertices) {
            transformation.transformPosition(vertex, transformType);
        }
    }

    private static void transform(Vector3d[] vertices, Matrix4dc transform) {
        for (Vector3d vertex : vertices) {
            transform.transformPosition(vertex);
        }
    }

    public AxisAlignedBB getEnclosedAABB() {
        if (this.enclosedBBCache == null) {
            Vector3dc firstVertex = this.vertices[0];
            double minX = firstVertex.x();
            double minY = firstVertex.y();
            double minZ = firstVertex.z();
            double maxX = firstVertex.x();
            double maxY = firstVertex.y();
            double maxZ = firstVertex.z();
            for (int i = 1; i < this.vertices.length; i++) {
                Vector3dc vertex = this.vertices[i];
                minX = Math.min(minX, vertex.x());
                minY = Math.min(minY, vertex.y());
                minZ = Math.min(minZ, vertex.z());
                maxX = Math.max(maxX, vertex.x());
                maxY = Math.max(maxY, vertex.y());
                maxZ = Math.max(maxZ, vertex.z());
            }
            this.enclosedBBCache = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
        }
        return this.enclosedBBCache;
    }

    public Vector3dc[] getVertices() {
        return this.vertices;
    }
}
