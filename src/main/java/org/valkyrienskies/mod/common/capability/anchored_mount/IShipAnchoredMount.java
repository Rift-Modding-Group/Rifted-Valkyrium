package org.valkyrienskies.mod.common.capability.anchored_mount;

import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;

/**
 * This capability is to allow compatibility with other mods that add
 * their own sittable blocks.
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
