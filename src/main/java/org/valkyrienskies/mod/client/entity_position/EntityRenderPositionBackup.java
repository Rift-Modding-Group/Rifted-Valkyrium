package org.valkyrienskies.mod.client.entity_position;

import net.minecraft.util.math.AxisAlignedBB;

/**
 * Stores temporary render-position state for entities so it can be restored after rendering.
 */
public record EntityRenderPositionBackup(
        double posX, double posY, double posZ,
        double lastTickPosX, double lastTickPosY, double lastTickPosZ,
        double prevPosX, double prevPosY, double prevPosZ,
        AxisAlignedBB boundingBox
) {}
