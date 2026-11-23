package valkyrienwarfare.api.event;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.common.eventhandler.Event;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import valkyrienwarfare.api.IPhysicsEntity;

import java.util.Objects;

/**
 * Posted on the Forge event bus on the server after a living entity leaves a ship-anchored
 * mount, before its exit position is applied. All positions are in ship coordinates.
 * The initial dismount position is the rider's current position in that frame for mounts
 * registered for ship alignment. Other anchored mounts use the nearest supported adjacent
 * position, falling back to the block above the anchor when none is available.
 * <p>
 * Handlers can replace {@link #getLocalDismountPos()} with {@link #setLocalDismountPos(Vec3d)}.
 * Built-in furniture placement runs at HIGH priority, so NORMAL or lower priority handlers
 * can override it. Rifted Valkyrium applies the final position, resets riding velocity,
 * transforms the rider's orientation, synchronizes the teleport, and retains ship tracking.
 * This event is not cancellable; the rider has already left the mount.
 */
public class ShipAnchoredEntityDismountEvent extends Event {
    private final EntityLivingBase rider;
    private final Entity mount;
    private final IPhysicsEntity ship;
    private final Vec3d localMountPos;
    private final BlockPos localAnchorBlock;
    private Vec3d localDismountPos;

    @ApiStatus.Internal
    public ShipAnchoredEntityDismountEvent(
            @NotNull EntityLivingBase rider, @NotNull Entity mount, @NotNull IPhysicsEntity ship,
            @NotNull Vec3d localMountPos, @NotNull BlockPos localAnchorBlock, @NotNull Vec3d localDismountPos
    ) {
        this.rider = Objects.requireNonNull(rider, "rider");
        this.mount = Objects.requireNonNull(mount, "mount");
        this.ship = Objects.requireNonNull(ship, "ship");
        this.localMountPos = Objects.requireNonNull(localMountPos, "localMountPos");
        this.localAnchorBlock = Objects.requireNonNull(localAnchorBlock, "localAnchorBlock").toImmutable();
        this.localDismountPos = Objects.requireNonNull(localDismountPos, "localDismountPos");
    }

    @NotNull
    public EntityLivingBase getRider() {
        return this.rider;
    }

    @NotNull
    public Entity getMount() {
        return this.mount;
    }

    @NotNull
    public IPhysicsEntity getShip() {
        return this.ship;
    }

    @NotNull
    public Vec3d getLocalMountPos() {
        return this.localMountPos;
    }

    @NotNull
    public BlockPos getLocalAnchorBlock() {
        return this.localAnchorBlock;
    }

    @NotNull
    public Vec3d getLocalDismountPos() {
        return this.localDismountPos;
    }

    public void setLocalDismountPos(@NotNull Vec3d localDismountPos) {
        this.localDismountPos = Objects.requireNonNull(localDismountPos, "localDismountPos");
    }
}
