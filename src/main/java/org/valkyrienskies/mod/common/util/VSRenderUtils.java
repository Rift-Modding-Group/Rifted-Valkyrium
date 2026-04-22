package org.valkyrienskies.mod.common.util;

import net.minecraft.entity.Entity;
import org.joml.Vector3d;

public final class VSRenderUtils {

    private VSRenderUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static Vector3d getEntityPartialPosition(Entity entity, float partialTicks) {
        double x = entity.lastTickPosX + (entity.posX - entity.lastTickPosX) * partialTicks;
        double y = entity.lastTickPosY + (entity.posY - entity.lastTickPosY) * partialTicks;
        double z = entity.lastTickPosZ + (entity.posZ - entity.lastTickPosZ) * partialTicks;

        return new Vector3d(x, y, z);
    }

}
