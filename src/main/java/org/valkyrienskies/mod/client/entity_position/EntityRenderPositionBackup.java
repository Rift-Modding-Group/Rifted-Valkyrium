package org.valkyrienskies.mod.client.entity_position;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import org.jetbrains.annotations.NotNull;

/**
 * Stores temporary render-position state so it can be restored after entity rendering.
 */
public record EntityRenderPositionBackup(
        double posX,
        double posY,
        double posZ,
        double lastTickPosX,
        double lastTickPosY,
        double lastTickPosZ,
        AxisAlignedBB boundingBox
) {

    public static EntityRenderPositionBackup of(@NotNull final Entity entity) {
        return new EntityRenderPositionBackup(
                entity.posX,
                entity.posY,
                entity.posZ,
                entity.lastTickPosX,
                entity.lastTickPosY,
                entity.lastTickPosZ,
                entity.getEntityBoundingBox());
    }

    public void restore(@NotNull final Entity entity) {
        entity.posX = posX;
        entity.posY = posY;
        entity.posZ = posZ;
        entity.lastTickPosX = lastTickPosX;
        entity.lastTickPosY = lastTickPosY;
        entity.lastTickPosZ = lastTickPosZ;
        entity.setEntityBoundingBox(boundingBox);
    }
}
