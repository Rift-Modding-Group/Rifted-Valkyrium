package org.valkyrienskies.mod.common.ships.pathfinding;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.init.Blocks;
import net.minecraft.pathfinding.NodeProcessor;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.pathfinding.PathNodeType;
import net.minecraft.pathfinding.WalkNodeProcessor;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Intersectiond;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.VSMath;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

/**
 * Bounded surface search shared by terrain and all nearby ships. Caches live for one search.
 * */
public class ShipPathfinder {
    private static final double MAX_JUMP_RISE = 1.25D;
    private final EntityLivingBase entity;
    @Nullable
    private final NodeProcessor processor;
    private final double speed;
    private final List<Frame> frames = new ArrayList<>();
    private final Map<SurfaceQuery, List<Surface>> surfaceCache = new HashMap<>();
    private final Vector3d localMin = new Vector3d();
    private final Vector3d localMax = new Vector3d();

    public ShipPathfinder(@NotNull EntityLiving entity, @NotNull PathNavigate navigator, double range) {
        this(entity, navigator, entity.getEntityBoundingBox().grow(range + 4D));
    }

    public ShipPathfinder(@NotNull EntityLivingBase entity, @Nullable PathNavigate navigator, @NotNull AxisAlignedBB searchBounds) {
        this.entity = entity;
        this.processor = navigator == null ? null : navigator.getNodeProcessor();
        this.speed = navigator != null && navigator.speed > 0D ? navigator.speed : 1D;
        this.frames.add(new Frame(null));
        IPhysObjectWorld manager = ValkyrienUtils.getPhysObjWorld(entity.world);
        if (manager != null) {
            for (PhysicsObject ship : manager.getPhysObjectsInAABB(searchBounds)) {
                if (!ship.getShipData().isPeggedTo(entity)) this.frames.add(new Frame(ship.getShipData()));
            }
        }
    }

    @NotNull
    public static Result findPath(@NotNull EntityLiving entity, @NotNull PathNavigate navigator, @NotNull Vec3d target) {
        if (entity.world.isRemote || !(navigator instanceof PathNavigateGround)
                || !(navigator.getNodeProcessor() instanceof WalkNodeProcessor) || entity.isRiding()
        ) {
            return new Result(false, null);
        }
        if (ShipPath.canReuse(navigator.getPath(), target)) return new Result(true, navigator.getPath());
        double range = Math.clamp(navigator.getPathSearchRange(), 1D, 128D);
        ShipPathfinder search = new ShipPathfinder(entity, navigator, range);
        if (search.frames.size() == 1) return new Result(false, null);

        // Block-target AI may already supply coordinates from the ship's storage chunks.
        for (Frame frame : search.frames) {
            if (frame.ship != null && frame.ship.getChunkClaim().containsChunk(MathHelper.floor(target.x) >> 4, MathHelper.floor(target.z) >> 4)) {
                target = frame.toWorld(target);
                break;
            }
        }
        if (ShipPath.canReuse(navigator.getPath(), target)) return new Result(true, navigator.getPath());
        Vec3d origin = entity.getPositionVector();
        List<Surface> starts = search.surfaces(origin.x, origin.z, origin.y - 1.25D, origin.y + 0.6D);
        Surface start = starts.stream().min(Comparator.comparingDouble(surface -> surface.position.squareDistanceTo(origin))).orElse(null);
        if (start == null) return new Result(true, null);

        ShipPath.Waypoint destination = new ShipPath.Waypoint(null, target, new Vec3d(0D, 1D, 0D));
        List<Surface> goals = search.surfaces(target.x, target.z, target.y - 2D, target.y + 1.125D);
        if (!goals.isEmpty()) {
            Vec3d requestedTarget = target;
            Surface goal = goals.stream().min(Comparator.comparingDouble(surface -> surface.position.squareDistanceTo(requestedTarget))).orElseThrow();
            destination = goal.waypoint;
            target = goal.position;
        }

        Map<ShipPath.Waypoint, SearchNode> nodes = new HashMap<>();
        PriorityQueue<QueueEntry> open = new PriorityQueue<>(Comparator.comparingDouble(QueueEntry::score));
        SearchNode first = new SearchNode(start);
        first.cost = 0D;
        nodes.put(start.waypoint, first);
        open.add(new QueueEntry(first, 0D, origin.distanceTo(target)));
        SearchNode closest = first;
        double closestDistance = first.surface.position.distanceTo(target);
        int visited = 0;
        while (!open.isEmpty() && visited++ < 512) {
            QueueEntry entry = open.remove();
            SearchNode current = entry.node;
            if (entry.cost != current.cost || current.closed) continue;
            current.closed = true;
            double distance = current.surface.position.distanceTo(target);
            if (distance < closestDistance) {
                closest = current;
                closestDistance = distance;
            }
            if (distance < 0.75D) {
                closest = current;
                break;
            }
            Vec3d from = current.surface.position;
            int baseX = MathHelper.floor(from.x);
            int baseZ = MathHelper.floor(from.z);
            for (int dx = -2; dx <= 2; dx++) {
                for (int dz = -2; dz <= 2; dz++) {
                    if ((dx == 0 && dz == 0) || Math.abs(dx) + Math.abs(dz) > 2) continue;
                    double x = baseX + dx + 0.5D;
                    double z = baseZ + dz + 0.5D;
                    for (Surface next : search.surfaces(x, z, from.y - entity.getMaxFallHeight(), from.y + Math.max(MAX_JUMP_RISE, entity.stepHeight))) {
                        if (next.position.squareDistanceTo(origin) > range * range) continue;
                        SearchNode neighbor = nodes.computeIfAbsent(next.waypoint, ignored -> new SearchNode(next));
                        if (neighbor.closed) continue;
                        double horizontal = Math.hypot(next.position.x - from.x, next.position.z - from.z);
                        boolean descending = next.position.y < from.y - 0.01D;
                        boolean jump = next.position.y - from.y > entity.stepHeight + 0.01D || horizontal > 1.5D && !descending;
                        if (!jump && search.surfaces((from.x + next.position.x) * 0.5D, (from.z + next.position.z) * 0.5D,
                                Math.min(from.y, next.position.y) - 0.6D, Math.max(from.y, next.position.y) + 0.1D, !descending).isEmpty()
                        ) {
                            jump = true;
                        }
                        double cost = current.cost + from.distanceTo(next.position) + next.penalty;
                        if (jump) cost += 1.5D;
                        if (cost >= neighbor.cost || !search.canTraverse(from, next.position, jump, false)) continue;
                        neighbor.cost = cost;
                        neighbor.previous = current;
                        neighbor.jump = jump;
                        open.add(new QueueEntry(neighbor, cost, cost + next.position.distanceTo(target)));
                    }
                }
            }
        }
        if (closest == first && closestDistance >= 0.75D) return new Result(true, null);
        List<ShipPath.Waypoint> waypoints = new ArrayList<>();
        List<Boolean> jumping = new ArrayList<>();
        for (SearchNode node = closest; node != null; node = node.previous) {
            waypoints.add(node.surface.waypoint);
            jumping.add(node.jump);
        }
        Collections.reverse(waypoints);
        Collections.reverse(jumping);
        boolean[] jumps = new boolean[jumping.size()];
        for (int index = 0; index < jumps.length; index++) jumps[index] = jumping.get(index);
        return new Result(true, new ShipPath(entity, waypoints, jumps, destination));
    }

    public static boolean canStandOnShip(@NotNull EntityLiving entity, @NotNull PathNavigate navigator, @NotNull BlockPos pos) {
        if (entity.world.isRemote || !(navigator instanceof PathNavigateGround)
                || !(navigator.getNodeProcessor() instanceof WalkNodeProcessor)) return false;
        ShipPathfinder search = new ShipPathfinder(entity, navigator, entity.getPositionVector().distanceTo(new Vec3d(pos)) + 1D);
        if (search.frames.size() == 1) return false;
        return search.surfaces(pos.getX() + 0.5D, pos.getZ() + 0.5D, pos.getY() - 0.6D, pos.getY() + 0.6D)
                .stream().anyMatch(surface -> surface.waypoint.ship() != null);
    }

    public static boolean teleportToShipNearOwner(@NotNull EntityLiving entity, @NotNull EntityLivingBase owner) {
        if (entity.world.isRemote || entity.world != owner.world) return false;
        PathNavigate navigator = entity.getNavigator();
        ShipPathfinder search = new ShipPathfinder(entity, navigator, owner.getEntityBoundingBox().grow(4D));
        if (search.frames.size() == 1) return false;
        int x = MathHelper.floor(owner.posX);
        int z = MathHelper.floor(owner.posZ);
        double y = owner.getEntityBoundingBox().minY;
        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                if (Math.abs(dx) < 2 && Math.abs(dz) < 2) continue;
                for (Surface surface : search.surfaces(x + dx + 0.5D, z + dz + 0.5D, y - 1.25D, y + 0.6D)) {
                    ShipData ship = surface.waypoint.ship();
                    if (ship == null || surface.penalty != 0F
                            || surface.type != PathNodeType.WALKABLE && surface.type != PathNodeType.OPEN) continue;
                    Vec3d position = surface.position;
                    entity.setLocationAndAngles(position.x, position.y, position.z, entity.rotationYaw, entity.rotationPitch);
                    entity.motionX = 0D;
                    entity.motionY = 0D;
                    entity.motionZ = 0D;
                    entity.fallDistance = 0f;
                    entity.onGround = true;
                    EntityDraggable.startTrackingShip(entity, ship);
                    navigator.clearPath();
                    return true;
                }
            }
        }
        return false;
    }

    @NotNull
    public static TeleportResult findTeleportLanding(@NotNull EntityLivingBase entity, @NotNull Vec3d target) {
        if (entity.world.isRemote || target.y <= 0D || !entity.world.isBlockLoaded(new BlockPos(target))) {
            return new TeleportResult(false, null);
        }
        double radius = entity.width * 0.5D;
        PathNavigate navigator = null;
        if (entity instanceof EntityLiving mob) navigator = mob.getNavigator();
        ShipPathfinder search = new ShipPathfinder(
                entity, navigator,
                new AxisAlignedBB(
                    target.x - radius, 0D, target.z - radius,
                    target.x + radius, target.y + entity.height, target.z + radius
                )
        );
        if (search.frames.size() == 1) return new TeleportResult(false, null);
        ShipPath.Waypoint landing = null;
        double highest = 0D;
        Vec3d start = target.add(0D, 0.01D, 0D);
        Vec3d end = new Vec3d(target.x, 0D, target.z);
        AxisAlignedBB column = new AxisAlignedBB(
                target.x - 0.001D, 0D, target.z - 0.001D,
                target.x + 0.001D, target.y + 0.01D, target.z + 0.001D
        );
        for (Shape shape : search.collisionShapes(column)) {
            RayTraceResult hit = shape.bounds.calculateIntercept(shape.frame.toLocal(start), shape.frame.toLocal(end));
            if (hit == null || hit.sideHit == null) continue;

            double height = shape.frame.toWorld(hit.hitVec).y;
            if (height <= highest) continue;

            highest = height;
            landing = new ShipPath.Waypoint(shape.frame.ship, hit.hitVec, new Vec3d(hit.sideHit.getDirectionVec()));
        }
        if (landing == null) return new TeleportResult(true, null);
        Vec3d position = landing.worldPosition(entity.width);
        for (Surface surface : search.surfaces(target.x, target.z, position.y - 0.2D, position.y + 0.2D)) {
            if (surface.waypoint.ship() == landing.ship() && surface.position.squareDistanceTo(position) < 1.0E-8D
                    && surface.penalty == 0F && (surface.type == PathNodeType.WALKABLE || surface.type == PathNodeType.OPEN)
            ) {
                return new TeleportResult(true, surface.waypoint);
            }
        }
        return new TeleportResult(true, null);
    }

    @NotNull
    public List<Surface> surfaces(double x, double z, double minY, double maxY) {
        return this.surfaces(x, z, minY, maxY, true);
    }

    @NotNull
    private List<Surface> surfaces(double x, double z, double minY, double maxY, boolean requireClearance) {
        SurfaceQuery query = new SurfaceQuery(x, z, minY, maxY, this.entity.width, this.entity.height, requireClearance);
        List<Surface> cached = this.surfaceCache.get(query);
        if (cached != null) return cached;
        List<Surface> result = new ArrayList<>();
        AxisAlignedBB column = new AxisAlignedBB(x - 0.001D, minY - 1D, z - 0.001D, x + 0.001D, maxY + 0.01D, z + 0.001D);
        for (Shape shape : this.collisionShapes(column)) {
            Vec3d start = shape.frame.toLocal(new Vec3d(x, maxY + 0.01D, z));
            Vec3d end = shape.frame.toLocal(new Vec3d(x, minY - 0.01D, z));
            RayTraceResult hit = shape.bounds.calculateIntercept(start, end);
            if (hit == null || hit.sideHit == null) continue;
            Vec3d normal = new Vec3d(hit.sideHit.getDirectionVec());
            Vec3d worldNormal = normal;
            if (shape.frame.transform != null) worldNormal = shape.frame.transform.rotate(normal, TransformType.SUBSPACE_TO_GLOBAL);
            if (worldNormal.y < 0.707D) continue;
            ShipPath.Waypoint waypoint = new ShipPath.Waypoint(shape.frame.ship, hit.hitVec, normal);
            Vec3d position = waypoint.worldPosition(this.entity.width);
            if (position.y < minY || position.y > maxY + 0.01D) continue;
            PathNodeType type = PathNodeType.WALKABLE;
            float penalty = 0F;
            if (this.processor != null && this.entity instanceof EntityLiving mob) {
                BlockPos feet = new BlockPos(hit.hitVec.x, Math.ceil(hit.hitVec.y - 0.001D), hit.hitVec.z);
                int width = MathHelper.ceil(this.entity.width);
                type = this.processor.getPathNodeType(
                        this.entity.world, feet.getX(), feet.getY(), feet.getZ(),
                        mob, width, MathHelper.ceil(this.entity.height), width,
                        this.processor.getCanOpenDoors(), this.processor.getCanEnterDoors()
                );
                penalty = mob.getPathPriority(type);
                PathNodeType supportType = this.processor.getPathNodeType(
                        this.entity.world,
                        shape.block.getX(), shape.block.getY(), shape.block.getZ()
                );
                if (penalty < 0f || supportType == PathNodeType.FENCE || supportType == PathNodeType.TRAPDOOR) continue;
            }
            // Descending routes check clearance above the ledge before lowering onto their landing.
            if (!requireClearance || this.isClear(position, position)) result.add(new Surface(waypoint, position, penalty, type));
        }
        this.surfaceCache.put(query, result);
        return result;
    }

    public boolean canTraverse(@NotNull Vec3d from, @NotNull Vec3d to, boolean jump) {
        return this.canTraverse(from, to, jump, true);
    }

    private boolean canTraverse(@NotNull Vec3d from, @NotNull Vec3d to, boolean jump, boolean checkSupport) {
        double horizontal = Math.hypot(to.x - from.x, to.z - from.z);
        double rise = to.y - from.y;
        if (horizontal > 2.25D || rise > Math.max(MAX_JUMP_RISE, this.entity.stepHeight) || rise < -this.entity.getMaxFallHeight() - 0.01D) {
            return false;
        }
        if (checkSupport && this.surfaces(to.x, to.z, to.y - 0.2D, to.y + 0.2D).isEmpty()) return false;
        if (checkSupport && !jump && this.surfaces((from.x + to.x) * 0.5D, (from.z + to.z) * 0.5D,
                Math.min(from.y, to.y) - 0.6D, Math.max(from.y, to.y) + 0.1D, rise >= -0.01D).isEmpty()
        ) {
            return false;
        }
        if (jump && horizontal > 1.5D
                && horizontal > this.entity.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue() * this.speed * 8D) {
            return false;
        }
        double travelY = Math.max(from.y, to.y);
        if (jump) travelY = from.y + MAX_JUMP_RISE;
        Vec3d raisedStart = new Vec3d(from.x, travelY, from.z);
        Vec3d raisedEnd = new Vec3d(to.x, travelY, to.z);
        if ((travelY <= from.y || this.isClear(from, raisedStart)) && this.isClear(raisedStart, raisedEnd)
                && (travelY <= to.y || this.isClear(raisedEnd, to))) return true;
        if (!jump) return false;

        // Check forward travel during the rise as well as the descent onto the landing.
        double horizontalSpeed = this.entity.getEntityAttribute(SharedMonsterAttributes.MOVEMENT_SPEED).getAttributeValue() * this.speed;
        double verticalSpeed = 0.42F;
        Vec3d previous = from;
        for (int tick = 1; tick <= 20; tick++) {
            double traveled = Math.min(horizontal, horizontalSpeed * tick);
            double progress = horizontal > 1.0E-7D ? traveled / horizontal : 1D;
            double y = previous.y + verticalSpeed;
            boolean landed = verticalSpeed < 0D && y <= to.y;
            if (landed) {
                if (traveled < horizontal) return false;
                y = to.y;
            }
            Vec3d next = new Vec3d(from.x + (to.x - from.x) * progress, y, from.z + (to.z - from.z) * progress);
            if (!this.isClear(previous, next)) return false;
            if (landed) return true;
            previous = next;
            verticalSpeed = (verticalSpeed - 0.08D) * 0.98D;
        }
        return false;
    }

    private boolean isClear(@NotNull Vec3d from, @NotNull Vec3d to) {
        double radius = this.entity.width * 0.5D - 0.001D;
        int steps = Math.max(1, MathHelper.ceil(from.distanceTo(to) * 4D));
        if (from.x == to.x || from.z == to.z) steps = 1;
        for (int step = 0; step < steps; step++) {
            Vec3d start = from.add(to.subtract(from).scale((double) step / steps));
            Vec3d end = from.add(to.subtract(from).scale((double) (step + 1) / steps));
            AxisAlignedBB swept = new AxisAlignedBB(
                    Math.min(start.x, end.x) - radius,
                    Math.min(start.y, end.y) + 0.001D,
                    Math.min(start.z, end.z) - radius,
                    Math.max(start.x, end.x) + radius,
                    Math.max(start.y, end.y) + this.entity.height - 0.001D,
                    Math.max(start.z, end.z) + radius
            );
            if (!this.entity.world.isAreaLoaded(new BlockPos(swept.minX, 0, swept.minZ), new BlockPos(swept.maxX, 0, swept.maxZ))) return false;
            for (Shape shape : this.collisionShapes(swept)) {
                if (shape.frame.transform == null) {
                    if (shape.bounds.intersects(swept)) return false;
                    continue;
                }
                if (Intersectiond.testObOb(
                    shape.center.x, shape.center.y, shape.center.z,
                    shape.frame.axisX.x, shape.frame.axisX.y, shape.frame.axisX.z,
                    shape.frame.axisY.x, shape.frame.axisY.y, shape.frame.axisY.z,
                    shape.frame.axisZ.x, shape.frame.axisZ.y, shape.frame.axisZ.z,
                    shape.halfExtents.x, shape.halfExtents.y, shape.halfExtents.z,
                    (swept.minX + swept.maxX) * 0.5D, (swept.minY + swept.maxY) * 0.5D, (swept.minZ + swept.maxZ) * 0.5D,
                    1D, 0D, 0D, 0D, 1D, 0D, 0D, 0D, 1D,
                    (swept.maxX - swept.minX) * 0.5D, (swept.maxY - swept.minY) * 0.5D, (swept.maxZ - swept.minZ) * 0.5D
            )) {
                    return false;
                }
            }
        }
        return true;
    }

    @NotNull
    private List<Shape> collisionShapes(@NotNull AxisAlignedBB bounds) {
        List<Shape> result = new ArrayList<>();
        for (Frame frame : this.frames) {
            if (frame.worldBounds != null && !frame.worldBounds.intersects(bounds)) continue;
            AxisAlignedBB local = bounds;
            if (frame.transform != null) {
                AxisAlignedBB clipped = bounds.intersect(frame.worldBounds);
                frame.transform.getGlobalToSubspace().transformAab(
                        clipped.minX, clipped.minY, clipped.minZ,
                        clipped.maxX, clipped.maxY, clipped.maxZ,
                        this.localMin, this.localMax
                );
                local = new AxisAlignedBB(this.localMin.x, this.localMin.y, this.localMin.z, this.localMax.x, this.localMax.y, this.localMax.z);
            }
            if (local.maxY < 0D || local.minY > 256D) continue;
            BlockPos min = new BlockPos(local.minX - 0.001D, Math.max(0D, local.minY - 1D), local.minZ - 0.001D);
            BlockPos max = new BlockPos(local.maxX + 0.001D, Math.min(255D, local.maxY + 0.001D), local.maxZ + 0.001D);
            for (BlockPos mutable : BlockPos.getAllInBoxMutable(min, max)) {
                if (frame.ship != null && !frame.ship.getChunkClaim().containsChunk(mutable.getX() >> 4, mutable.getZ() >> 4)) continue;
                long key = mutable.toLong();
                List<Shape> cached = frame.shapes.get(key);
                if (cached == null) {
                    cached = List.of();
                    if (this.entity.world.isBlockLoaded(mutable)) {
                        IBlockState state = this.entity.world.getBlockState(mutable);
                        if (state.getBlock() != Blocks.AIR) {
                            List<AxisAlignedBB> boxes = new ArrayList<>();
                            state.addCollisionBoxToList(this.entity.world, mutable, new AxisAlignedBB(mutable).grow(1D), boxes, this.entity, false);
                            if (!boxes.isEmpty()) {
                                cached = new ArrayList<>(boxes.size());
                                BlockPos block = mutable.toImmutable();
                                for (AxisAlignedBB box : boxes) {
                                    Vec3d center = frame.toWorld(VSMath.getCenter(box));
                                    Vec3d half = new Vec3d((box.maxX - box.minX) * 0.5D, (box.maxY - box.minY) * 0.5D, (box.maxZ - box.minZ) * 0.5D);
                                    double rx = Math.abs(frame.axisX.x) * half.x + Math.abs(frame.axisY.x) * half.y + Math.abs(frame.axisZ.x) * half.z;
                                    double ry = Math.abs(frame.axisX.y) * half.x + Math.abs(frame.axisY.y) * half.y + Math.abs(frame.axisZ.y) * half.z;
                                    double rz = Math.abs(frame.axisX.z) * half.x + Math.abs(frame.axisY.z) * half.y + Math.abs(frame.axisZ.z) * half.z;
                                    AxisAlignedBB worldBounds = new AxisAlignedBB(center.x - rx, center.y - ry, center.z - rz, center.x + rx, center.y + ry, center.z + rz);
                                    cached.add(new Shape(frame, block, box, worldBounds, center, half));
                                }
                            }
                        }
                    }
                    frame.shapes.put(key, cached);
                }
                if (cached.isEmpty()) continue;
                for (Shape shape : cached) {
                    if (shape.worldBounds.intersects(bounds)) result.add(shape);
                }
            }
        }
        return result;
    }

    public record Result(boolean handled, @Nullable Path path) {}

    public record TeleportResult(boolean handled, @Nullable ShipPath.Waypoint landing) {}

    private record SurfaceQuery(double x, double z, double minY, double maxY, float width, float height, boolean requireClearance) {}

    public record Surface(@NotNull ShipPath.Waypoint waypoint, @NotNull Vec3d position, float penalty, @NotNull PathNodeType type) {}

    private record QueueEntry(@NotNull SearchNode node, double cost, double score) {}

    private static class SearchNode {
        private final Surface surface;
        private double cost = Double.MAX_VALUE;
        private boolean closed;
        private boolean jump;
        @Nullable
        private SearchNode previous;

        private SearchNode(@NotNull Surface surface) {
            this.surface = surface;
        }
    }

    private static class Frame {
        @Nullable
        private final ShipData ship;
        @Nullable
        private ShipTransform transform;
        @Nullable
        private AxisAlignedBB worldBounds;
        private final Long2ObjectOpenHashMap<List<Shape>> shapes = new Long2ObjectOpenHashMap<>();
        private final Vector3d axisX = new Vector3d(1D, 0D, 0D);
        private final Vector3d axisY = new Vector3d(0D, 1D, 0D);
        private final Vector3d axisZ = new Vector3d(0D, 0D, 1D);

        private Frame(@Nullable ShipData ship) {
            this.ship = ship;
            if (ship != null) {
                this.transform = ship.getShipTransform();
                this.worldBounds = ship.getShipBB().grow(1D);
                this.transform.transformDirection(this.axisX, TransformType.SUBSPACE_TO_GLOBAL);
                this.transform.transformDirection(this.axisY, TransformType.SUBSPACE_TO_GLOBAL);
                this.transform.transformDirection(this.axisZ, TransformType.SUBSPACE_TO_GLOBAL);
            }
        }

        @NotNull
        private Vec3d toWorld(@NotNull Vec3d local) {
            return this.transform == null ? local : this.transform.transform(local, TransformType.SUBSPACE_TO_GLOBAL);
        }

        @NotNull
        private Vec3d toLocal(@NotNull Vec3d world) {
            return this.transform == null ? world : this.transform.transform(world, TransformType.GLOBAL_TO_SUBSPACE);
        }
    }

    private record Shape(
            @NotNull Frame frame, @NotNull BlockPos block, @NotNull AxisAlignedBB bounds,
            @NotNull AxisAlignedBB worldBounds, @NotNull Vec3d center, @NotNull Vec3d halfExtents
    ) {}
}
