package org.valkyrienskies.mod.common.ships.pathfinding;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathPoint;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.List;

/**
 * A route whose ship waypoints stay attached to their supporting blocks.
 * */
public class ShipPath extends Path {
    private final EntityLiving entity;
    private final List<Waypoint> waypoints;
    private final boolean[] jumps;
    private final Waypoint destination;
    private int ticks;
    private int progressTick;
    private int progressIndex = -1;
    private double nearestDistance = Double.MAX_VALUE;

    public ShipPath(@NotNull EntityLiving entity, @NotNull List<Waypoint> waypoints, boolean[] jumps, @NotNull Waypoint destination) {
        super(waypoints.stream().map(point -> {
            Vec3d position = point.worldPosition(entity.width);
            return new PathPoint(MathHelper.floor(position.x), MathHelper.floor(position.y), MathHelper.floor(position.z));
        }).toArray(PathPoint[]::new));
        this.entity = entity;
        this.waypoints = List.copyOf(waypoints);
        this.jumps = jumps;
        this.destination = destination;
    }

    @Override
    @NotNull
    public Vec3d getVectorFromIndex(@NotNull Entity entityIn, int index) {
        return this.waypoints.get(index).worldPosition(entityIn.width);
    }

    @Override
    @NotNull
    public PathPoint getPathPointFromIndex(int index) {
        Vec3d point = this.getVectorFromIndex(this.entity, index);
        return new PathPoint(MathHelper.floor(point.x), MathHelper.floor(point.y), MathHelper.floor(point.z));
    }

    @Override
    @Nullable
    public PathPoint getFinalPathPoint() {
        return this.getCurrentPathLength() == 0 ? null : this.getPathPointFromIndex(this.getCurrentPathLength() - 1);
    }

    @Override
    @NotNull
    public Vec3d getCurrentPos() {
        PathPoint point = this.getPathPointFromIndex(this.getCurrentPathIndex());
        return new Vec3d(point.x, point.y, point.z);
    }

    @Override
    public boolean isSamePath(@Nullable Path other) {
        return other instanceof ShipPath shipPath && this.waypoints.equals(shipPath.waypoints) && this.destination.equals(shipPath.destination);
    }

    public static boolean canReuse(@Nullable Path candidate, @NotNull Vec3d target) {
        return candidate instanceof ShipPath path && !path.isFinished() && path.destination.worldPosition(0D).squareDistanceTo(target) < 0.25D;
    }

    public void update(@NotNull PathNavigate navigator, boolean repath) {
        if (this.isFinished()) return;

        IPhysObjectWorld manager = ValkyrienUtils.getPhysObjWorld(this.entity.world);
        ShipData checkedShip = null;
        for (int index = this.getCurrentPathIndex(); index < this.getCurrentPathLength(); index++) {
            ShipData ship = this.waypoints.get(index).ship();
            if (ship == null || ship == checkedShip) continue;
            if (manager == null || manager.getPhysObjectFromUUID(ship.getUuid()) == null) {
                navigator.clearPath();
                return;
            }
            checkedShip = ship;
        }
        if (repath) {
            Vec3d target = this.destination.worldPosition(0D);
            navigator.clearPath();
            ShipPathfinder.Result result = ShipPathfinder.findPath(this.entity, navigator, target);
            Path replacement = result.path();
            if (!result.handled()) replacement = navigator.getPathToXYZ(target.x, target.y, target.z);
            navigator.setPath(replacement, navigator.speed);
            return;
        }

        this.ticks++;
        int index = this.getCurrentPathIndex();
        Vec3d target = this.getVectorFromIndex(this.entity, index);
        double tolerance = Math.clamp(this.entity.width * 0.5D, 0.2D, 0.45D);
        double dx = target.x - this.entity.posX;
        double dz = target.z - this.entity.posZ;
        if (Math.abs(dx) < tolerance && Math.abs(dz) < tolerance
                && (Math.abs(target.y - this.entity.posY) < 0.5D && this.entity.onGround
                    || !this.entity.onGround && this.entity.posY > target.y)) {
            this.incrementPathIndex();
            if (this.isFinished()) return;
            index = this.getCurrentPathIndex();
            target = this.getVectorFromIndex(this.entity, index);
        }

        double distance = this.entity.getPositionVector().squareDistanceTo(target);
        if (index != this.progressIndex || distance < this.nearestDistance - 0.05D) {
            this.progressIndex = index;
            this.progressTick = this.ticks;
            this.nearestDistance = distance;
        }
        if (this.ticks - this.progressTick > 80) {
            navigator.clearPath();
            return;
        }

        if ((this.ticks + this.entity.getEntityId()) % 10 == 0 && this.entity.onGround) {
            ShipPathfinder search = new ShipPathfinder(this.entity, navigator, 4D);
            Vec3d position = this.entity.getPositionVector();
            if (!search.canTraverse(position, target, this.jumps[index])) {
                this.update(navigator, true);
                return;
            }
        }
        this.entity.getMoveHelper().setMoveTo(target.x, target.y, target.z, navigator.speed);
        if (this.jumps[index] && this.entity.onGround && index > 0) {
            Vec3d launch = this.getVectorFromIndex(this.entity, index - 1);
            if (this.entity.getPositionVector().squareDistanceTo(launch) < 0.64D) {
                this.entity.getJumpHelper().setJumping();
            }
        }
    }

    public record Waypoint(@Nullable ShipData ship, @NotNull Vec3d position, @NotNull Vec3d normal) {
        @NotNull
        public Vec3d worldPosition(double width) {
            Vec3d result = this.position;
            Vec3d up = this.normal;
            if (this.ship != null) {
                result = this.ship.getShipTransform().transform(result, TransformType.SUBSPACE_TO_GLOBAL);
                up = this.ship.getShipTransform().rotate(up, TransformType.SUBSPACE_TO_GLOBAL);
            }
            if (up.y > 0.01D) {
                double clearance = width * 0.5D * (Math.abs(up.x) + Math.abs(up.z)) / up.y;
                result = result.add(0D, clearance + 0.002D, 0D);
            }
            return result;
        }
    }
}
