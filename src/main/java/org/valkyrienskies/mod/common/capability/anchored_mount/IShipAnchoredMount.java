package org.valkyrienskies.mod.common.capability.anchored_mount;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

/**
 * Helps out with making aligned entities set to align w ships using {@link valkyrienwarfare.api.RiftedValkyriumAPI#setEntityAlignWithShip(Class)}
 * align to the ship, of course.
 * TODO: remove everything relating to compat w config sittable entities
 * */
public interface IShipAnchoredMount {
    boolean isAnchoredToShip();

    @NotNull
    Vec3d getLocalMountPos();

    @NotNull
    BlockPos getLocalAnchorBlock();

    void setAnchorMountData(@NotNull Vec3d localMountPos, @NotNull BlockPos localAnchorBlock);

    boolean tryAnchorMount(@NotNull Entity entity);
}
