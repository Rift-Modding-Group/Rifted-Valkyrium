package org.valkyrienskies.mod.common.ships.fluids;

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import net.minecraft.block.BlockDoor;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.BlockFaceShape;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.fluids.IFluidBlock;
import net.minecraftforge.fml.common.Loader;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.compat.FluidloggedAPICompat;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.chunk_claims.ShipChunkAllocator;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.mod.common.util.TransformedAABB;
import valkyrienwarfare.api.TransformType;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.List;
import java.util.BitSet;
import java.util.Arrays;
import java.util.function.IntUnaryOperator;

/**
 * Ship-local water connectivity. Snapshots are published on the game thread for physics and rendering.
 */
@ParametersAreNonnullByDefault
public class ShipWaterVolume {
    //dense grids are bounded to two million cells, use section storage for larger hulls
    private static final int MAX_VOLUME = 2_000_000;
    private volatile Snapshot snapshot = new Snapshot(BlockPos.ORIGIN, 0, 0, 0, new BitSet(), new BitSet(), new BitSet());
    private boolean dirty = true;
    private boolean hullBoundsDirty = true;
    @Nullable
    private AxisAlignedBB hullBounds;
    private int inflowTicks;
    private boolean volumeLimitWarned;
    private BlockPos origin = BlockPos.ORIGIN;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private byte[] openFaces = new byte[0];
    private final BitSet localFluid = new BitSet();
    private final BitSet localWater = new BitSet();
    private final BitSet air = new BitSet();
    private BitSet interior = new BitSet();
    private final BitSet changedCells = new BitSet();
    private final BitSet acceptsWater = new BitSet();
    private final BitSet passages = new BitSet();
    private final Long2DoubleOpenHashMap waterHeights = new Long2DoubleOpenHashMap();

    public void markDirty() {
        this.dirty = true;
        this.hullBoundsDirty = true;
    }

    public void onBlockUpdate(BlockPos pos, IBlockState oldState, IBlockState newState) {
        boolean oldHull = oldState.getMaterial() != Material.AIR && !oldState.getMaterial().isLiquid()
                && !(oldState.getBlock() instanceof IFluidBlock);
        boolean newHull = newState.getMaterial() != Material.AIR && !newState.getMaterial().isLiquid()
                && !(newState.getBlock() instanceof IFluidBlock);
        if (oldState != newState && (oldHull || newHull)) this.markDirty();
        else this.markFluidDirty(pos);
    }

    public void markFluidDirty(BlockPos pos) {
        this.markFluidDirty(pos.getX(), pos.getY(), pos.getZ(), pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Coalesces changed fluid cells and their neighbors without invalidating structural bounds.
     * */
    public void markFluidDirty(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        if (this.dirty) return;
        int startX = Math.max(0, minX - this.origin.getX() - 1);
        int endX = Math.min(this.sizeX - 1, maxX - this.origin.getX() + 1);
        int startY = Math.max(0, minY - this.origin.getY() - 1);
        int endY = Math.min(this.sizeY - 1, maxY - this.origin.getY() + 1);
        int startZ = Math.max(0, minZ - this.origin.getZ() - 1);
        int endZ = Math.min(this.sizeZ - 1, maxZ - this.origin.getZ() + 1);
        if (startX > endX) return;
        for (int z = startZ; z <= endZ; z++) {
            for (int y = startY; y <= endY; y++) {
                int base = this.sizeX * (y + this.sizeY * z);
                this.changedCells.set(base + startX, base + endX + 1);
            }
        }
    }

    public Snapshot getSnapshot() {
        return this.snapshot;
    }

    public void update(PhysicsObject ship) {
        World world = ship.getWorld();
        boolean facesChanged = this.dirty;
        if (this.dirty) {
            AxisAlignedBB bounds = getHullBounds(ship);
            this.sizeX = bounds == null ? 0 : (int) (bounds.maxX - bounds.minX) + 1;
            this.sizeY = bounds == null ? 0 : (int) (bounds.maxY - bounds.minY) + 1;
            this.sizeZ = bounds == null ? 0 : (int) (bounds.maxZ - bounds.minZ) + 1;
            long volume = (long) this.sizeX * this.sizeY * this.sizeZ;
            if (volume > MAX_VOLUME || volume == 0) {
                if (volume > MAX_VOLUME && !this.volumeLimitWarned) {
                    ValkyrienSkiesMod.LOGGER.warn("Water sealing skipped for ship {}: hull bounds contain {} cells (limit {}).",
                            ship.getUuid(), volume, MAX_VOLUME);
                    this.volumeLimitWarned = true;
                }
                this.snapshot = new Snapshot(BlockPos.ORIGIN, 0, 0, 0, new BitSet(), new BitSet(), new BitSet());
                return;
            }
            this.volumeLimitWarned = false;
            this.origin = new BlockPos(bounds.minX, bounds.minY, bounds.minZ);
            this.openFaces = new byte[(int) volume];
            this.localFluid.clear();
            this.localWater.clear();
            this.air.clear();
            this.acceptsWater.clear();
            this.passages.clear();
            this.changedCells.clear();
            this.changedCells.set(0, (int) volume);
        }
        if (!this.changedCells.isEmpty()) {
            boolean fluidlogged = Loader.isModLoaded("fluidlogged_api");
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int index = this.changedCells.nextSetBit(0); index >= 0; index = this.changedCells.nextSetBit(index + 1)) {
                pos.setPos(this.origin.getX() + index % this.sizeX, this.origin.getY() + index / this.sizeX % this.sizeY,
                        this.origin.getZ() + index / (this.sizeX * this.sizeY));
                byte faces = 63;
                this.localFluid.clear(index);
                this.localWater.clear(index);
                this.air.set(index);
                this.acceptsWater.clear(index);
                this.passages.clear(index);
                if (ship.getChunkClaim().containsBlock(pos)) {
                    Chunk chunk = ship.getChunkAt(pos.getX() >> 4, pos.getZ() >> 4);
                    if (chunk == null || !chunk.isLoaded() || chunk.isEmpty()) {
                        this.markDirty();
                        this.snapshot = new Snapshot(BlockPos.ORIGIN, 0, 0, 0, new BitSet(), new BitSet(), new BitSet());
                        return;
                    }
                    IBlockState state = chunk.getBlockState(pos);
                    IBlockState actual = state.getActualState(world, pos);
                    this.air.set(index, state.getMaterial() == Material.AIR);
                    IBlockState fluid = state;
                    if (fluidlogged) fluid = FluidloggedAPICompat.getFluidOrReal(world, pos);
                    this.localFluid.set(index, fluid.getMaterial().isLiquid());
                    this.localWater.set(index, fluid.getMaterial() == Material.WATER);
                    faces = 0;
                    if (!(actual.getBlock() instanceof BlockDoor) || actual.getValue(BlockDoor.OPEN)) {
                        for (EnumFacing side : EnumFacing.values()) {
                            boolean open;
                            if (fluidlogged) open = FluidloggedAPICompat.canWaterFlow(world, pos, actual, side);
                            else open = actual.getBlockFaceShape(world, pos, side) != BlockFaceShape.SOLID;
                            if (open) faces |= (byte) (1 << side.getIndex());
                        }
                    }
                    if (!world.isRemote && faces != 0) {
                        boolean accepts;
                        if (fluidlogged) accepts = FluidloggedAPICompat.canAdmitWater(world, pos, state);
                        else accepts = state.getMaterial() == Material.AIR
                                || state.getMaterial() == Material.WATER && state.getBlock() instanceof BlockLiquid && state.getValue(BlockLiquid.LEVEL) != 0;
                        this.acceptsWater.set(index, accepts);
                        this.passages.set(index, !this.localFluid.get(index));
                    }
                }
                if (this.openFaces[index] != faces) facesChanged = true;
                this.openFaces[index] = faces;
            }
            if (facesChanged) this.interior = findInteriorCells(this.sizeX, this.sizeY, this.sizeZ, this.openFaces);
            this.changedCells.clear();
            this.dirty = false;
        }

        ShipTransform transform = ship.getShipTransform();
        this.waterHeights.clear();
        byte[] wetFaces = new byte[this.openFaces.length];
        Arrays.fill(wetFaces, (byte) -1);
        Vector3d center = new Vector3d();
        Vector3d normal = new Vector3d();
        double verticalExtent = 0.5D * (Math.abs(transform.getSubspaceToGlobal().m01())
                + Math.abs(transform.getSubspaceToGlobal().m11()) + Math.abs(transform.getSubspaceToGlobal().m21()));
        BitSet dry = findDryCells(this.sizeX, this.sizeY, this.sizeZ, this.openFaces, index -> {
            if (wetFaces[index] >= 0) return wetFaces[index];
            int wet = 0;
            BlockPos pos = this.origin.add(index % this.sizeX, index / this.sizeX % this.sizeY, index / (this.sizeX * this.sizeY));
            center.set(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D);
            transform.transformPosition(center, TransformType.SUBSPACE_TO_GLOBAL);
            for (EnumFacing side : EnumFacing.values()) {
                if ((this.openFaces[index] & (1 << side.getIndex())) == 0) continue;
                normal.set(side.getXOffset(), side.getYOffset(), side.getZOffset());
                transform.transformDirection(normal, TransformType.SUBSPACE_TO_GLOBAL);
                double faceBottom = center.y + normal.y * 0.5D - verticalExtent + Math.abs(normal.y) * 0.5D;
                double sampleX = center.x + normal.x * 0.5001D;
                double sampleZ = center.z + normal.z * 0.5001D;
                double sampleY = faceBottom + 0.0001D;
                long sample = new BlockPos(sampleX, sampleY, sampleZ).toLong();
                double height = this.waterHeights.getOrDefault(sample, -Double.MAX_VALUE);
                if (height == -Double.MAX_VALUE) {
                    height = waterHeight(world, sampleX, sampleY, sampleZ);
                    if (height > sampleY) this.waterHeights.put(sample, height);
                }
                if (height > sampleY) {
                    wet |= 1 << side.getIndex();
                }
            }
            wetFaces[index] = (byte) wet;
            return wet;
        }, new BitSet());
        if (!world.isRemote && ++this.inflowTicks >= Blocks.FLOWING_WATER.tickRate(world)) {
            this.inflowTicks = 0;
            BitSet fillable = (BitSet) this.acceptsWater.clone();
            fillable.andNot(dry);
            fillable.and(this.interior);
            BitSet passages = (BitSet) this.passages.clone();
            passages.andNot(dry);
            passages.andNot(fillable);
            boolean fluidlogged = Loader.isModLoaded("fluidlogged_api");
            BitSet intake = findInflowCells(this.sizeX, this.sizeY, this.sizeZ, this.openFaces, wetFaces,
                    fillable, passages, this.localWater);
            List<BlockPos> incoming = new ArrayList<>();
            for (int index = intake.nextSetBit(0); index >= 0 && incoming.size() < 64; index = intake.nextSetBit(index + 1)) {
                incoming.add(this.origin.add(index % this.sizeX, index / this.sizeX % this.sizeY, index / (this.sizeX * this.sizeY)));
            }
            for (BlockPos pos : incoming) {
                boolean placed;
                if (fluidlogged) placed = FluidloggedAPICompat.admitWater(world, pos);
                else {
                    IBlockState state = world.getBlockState(pos);
                    if (state.getMaterial() != Material.AIR && (state.getMaterial() != Material.WATER
                            || !(state.getBlock() instanceof BlockLiquid) || state.getValue(BlockLiquid.LEVEL) == 0)) continue;
                    placed = world.setBlockState(pos, Blocks.FLOWING_WATER.getDefaultState(), 3);
                }
                if (!placed) continue;
                int index = pos.getX() - this.origin.getX() + this.sizeX * (pos.getY() - this.origin.getY()
                        + this.sizeY * (pos.getZ() - this.origin.getZ()));
                this.localFluid.set(index);
                this.localWater.set(index);
                this.air.clear(index);
                this.acceptsWater.clear(index);
                this.passages.clear(index);
                this.markFluidDirty(pos);
            }
        }
        dry.or(this.interior);
        dry.andNot(this.localFluid);
        BitSet mask = (BitSet) dry.clone();
        mask.or(this.localWater);
        BitSet displacedAir = (BitSet) dry.clone();
        displacedAir.and(this.air);
        Snapshot previous = this.snapshot;
        if (!previous.origin().equals(this.origin) || previous.sizeX() != this.sizeX || previous.sizeY() != this.sizeY
                || previous.sizeZ() != this.sizeZ || !previous.dry().equals(dry) || !previous.air().equals(displacedAir) || !previous.mask().equals(mask)
        ) {
            this.snapshot = new Snapshot(this.origin, this.sizeX, this.sizeY, this.sizeZ, dry, displacedAir, mask);
        }
    }

    public static boolean canFlowDown(PhysicsObject ship, BlockPos sourcePos) {
        AxisAlignedBB bounds = getHullBounds(ship);
        return bounds != null && sourcePos.getY() > bounds.minY;
    }

    /**
     * Hull bounds follow non-fluid blocks, so escaping water cannot extend the ship.
     * */
    @Nullable
    public static AxisAlignedBB getHullBounds(PhysicsObject ship) {
        ShipWaterVolume volume = ship.getWaterVolume();
        if (!volume.hullBoundsDirty) return volume.hullBounds;
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        if (ship.getBlockPositions() != null) {
            for (BlockPos pos : ship.getBlockPositions()) {
                IBlockState state = ship.getWorld().getBlockState(pos);
                if (state.getMaterial() == Material.AIR || state.getMaterial().isLiquid() || state.getBlock() instanceof IFluidBlock) continue;
                minX = Math.min(minX, pos.getX());
                minY = Math.min(minY, pos.getY());
                minZ = Math.min(minZ, pos.getZ());
                maxX = Math.max(maxX, pos.getX());
                maxY = Math.max(maxY, pos.getY());
                maxZ = Math.max(maxZ, pos.getZ());
            }
        }
        else {
            for (Chunk chunk : ship.getClaimedChunkCache()) {
                if (chunk == null || !chunk.isLoaded() || chunk.isEmpty()) return null;
                for (ExtendedBlockStorage section : chunk.getBlockStorageArray()) {
                    if (section == null || section.isEmpty()) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                IBlockState state = section.get(x, y, z);
                                Material material = state.getMaterial();
                                if (material == Material.AIR || material.isLiquid() || state.getBlock() instanceof IFluidBlock) continue;
                                minX = Math.min(minX, (chunk.x << 4) + x);
                                minY = Math.min(minY, section.getYLocation() + y);
                                minZ = Math.min(minZ, (chunk.z << 4) + z);
                                maxX = Math.max(maxX, (chunk.x << 4) + x);
                                maxY = Math.max(maxY, section.getYLocation() + y);
                                maxZ = Math.max(maxZ, (chunk.z << 4) + z);
                            }
                        }
                    }
                }
            }
        }
        volume.hullBounds = null;
        if (minX <= maxX) volume.hullBounds = new AxisAlignedBB(minX, minY, minZ, maxX, maxY, maxZ);
        volume.hullBoundsDirty = false;
        return volume.hullBounds;
    }

    /** Finds the next water front, allowing water to pass through open doors without replacing them. */
    public static BitSet findInflowCells(int sizeX, int sizeY, int sizeZ, byte[] openFaces, byte[] wetFaces,
                                         BitSet fillable, BitSet passages, BitSet water) {
        int volume = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        if (openFaces.length != volume || wetFaces.length != volume) throw new IllegalArgumentException("Invalid water grid");
        BitSet incoming = new BitSet(volume);
        BitSet supplied = (BitSet) water.clone();
        ArrayDeque<Integer> pending = new ArrayDeque<>();
        for (int index = 0; index < volume; index++) {
            if (water.get(index)) pending.add(index);
            if ((openFaces[index] & wetFaces[index] & boundaryFaces(index, sizeX, sizeY, sizeZ)) == 0) continue;
            if (fillable.get(index)) incoming.set(index);
            else if (passages.get(index) && !supplied.get(index)) {
                supplied.set(index);
                pending.add(index);
            }
        }
        while (!pending.isEmpty()) {
            int index = pending.remove();
            int x = index % sizeX;
            int y = index / sizeX % sizeY;
            int z = index / (sizeX * sizeY);
            for (EnumFacing side : EnumFacing.values()) {
                if ((openFaces[index] & wetFaces[index] & (1 << side.getIndex())) == 0) continue;
                int nx = x + side.getXOffset();
                int ny = y + side.getYOffset();
                int nz = z + side.getZOffset();
                if (nx < 0 || nx >= sizeX || ny < 0 || ny >= sizeY || nz < 0 || nz >= sizeZ) continue;
                int neighbor = nx + sizeX * (ny + sizeY * nz);
                if (incoming.get(neighbor) || supplied.get(neighbor)
                        || (openFaces[neighbor] & (1 << side.getOpposite().getIndex())) == 0) continue;
                if (fillable.get(neighbor)) incoming.set(neighbor);
                else if (passages.get(neighbor)) {
                    supplied.set(neighbor);
                    pending.add(neighbor);
                }
            }
        }
        return incoming;
    }

    private static int boundaryFaces(int index, int sizeX, int sizeY, int sizeZ) {
        int x = index % sizeX;
        int y = index / sizeX % sizeY;
        int z = index / (sizeX * sizeY);
        int boundary = 0;
        if (y == 0) boundary |= 1 << EnumFacing.DOWN.getIndex();
        if (y == sizeY - 1) boundary |= 1 << EnumFacing.UP.getIndex();
        if (z == 0) boundary |= 1 << EnumFacing.NORTH.getIndex();
        if (z == sizeZ - 1) boundary |= 1 << EnumFacing.SOUTH.getIndex();
        if (x == 0) boundary |= 1 << EnumFacing.WEST.getIndex();
        if (x == sizeX - 1) boundary |= 1 << EnumFacing.EAST.getIndex();
        return boundary;
    }

    /**
     * Marks the space spanned by opposing hull blocks, including their breached cells.
     * */
    public static BitSet findInteriorCells(int sizeX, int sizeY, int sizeZ, byte[] openFaces) {
        int volume = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        if (openFaces.length != volume) throw new IllegalArgumentException("Invalid water grid");
        BitSet interior = new BitSet(volume);
        for (EnumFacing axis : new EnumFacing[]{EnumFacing.EAST, EnumFacing.UP, EnumFacing.SOUTH}) {
            int length;
            int stride;
            switch (axis) {
                case EAST -> {
                    length = sizeX;
                    stride = 1;
                }
                case UP -> {
                    length = sizeY;
                    stride = sizeX;
                }
                default -> {
                    length = sizeZ;
                    stride = sizeX * sizeY;
                }
            }
            for (int base = 0; base < volume; base++) {
                if (base / stride % length != 0) continue;
                int first = -1;
                int last = -1;
                for (int offset = 0; offset < length; offset++) {
                    int faces = openFaces[base + stride * offset];
                    int axisFaces = (1 << axis.getIndex()) | (1 << axis.getOpposite().getIndex());
                    if ((faces & axisFaces) == axisFaces) continue;
                    if (first < 0) first = offset;
                    last = offset;
                }
                if (first < 0 || first == last) continue;
                for (int offset = first; offset <= last; offset++) interior.set(base + stride * offset);
            }
        }
        return interior;
    }

    /**
     * Follows continuous open faces touching water, leaving openings above the waterline dry.
     */
    public static BitSet findDryCells(int sizeX, int sizeY, int sizeZ, byte[] openFaces, IntUnaryOperator wetFaces, BitSet localFluid) {
        int volume = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        if (openFaces.length != volume) throw new IllegalArgumentException("Invalid water grid");
        BitSet flooded = new BitSet(volume);
        int[] pending = new int[volume];
        int head = 0;
        int tail = 0;
        for (int index = 0; index < volume; index++) {
            int boundary = boundaryFaces(index, sizeX, sizeY, sizeZ);
            if ((openFaces[index] & boundary) != 0 && (openFaces[index] & wetFaces.applyAsInt(index) & boundary) != 0) {
                flooded.set(index);
                pending[tail++] = index;
            }
        }
        while (head < tail) {
            int index = pending[head++];
            int x = index % sizeX;
            int y = index / sizeX % sizeY;
            int z = index / (sizeX * sizeY);
            int wet = wetFaces.applyAsInt(index);
            for (EnumFacing side : EnumFacing.values()) {
                if ((openFaces[index] & wet & (1 << side.getIndex())) == 0) continue;
                int nx = x + side.getXOffset();
                int ny = y + side.getYOffset();
                int nz = z + side.getZOffset();
                if (nx < 0 || nx >= sizeX || ny < 0 || ny >= sizeY || nz < 0 || nz >= sizeZ) continue;
                int neighbor = nx + sizeX * (ny + sizeY * nz);
                if (flooded.get(neighbor) || (openFaces[neighbor] & (1 << side.getOpposite().getIndex())) == 0) continue;
                flooded.set(neighbor);
                pending[tail++] = neighbor;
            }
        }
        BitSet dry = new BitSet(volume);
        for (int index = 0; index < volume; index++) {
            if (!flooded.get(index) && !localFluid.get(index)) dry.set(index);
        }
        return dry;
    }

    /**
     * Returns the unmasked world water surface at the sample, without loading terrain chunks.
     * */
    public static double waterHeight(World world, double x, double y, double z) {
        BlockPos pos = new BlockPos(x, y, z);
        if (world.isOutsideBuildHeight(pos) || !world.isBlockLoaded(pos, false)) return y;
        IBlockState state = world.getBlockState(pos);
        if (Loader.isModLoaded("fluidlogged_api")) return FluidloggedAPICompat.getWaterHeight(world, pos, y);
        if (state.getMaterial() != Material.WATER) return y;
        if (world.getBlockState(pos.up()).getMaterial() == Material.WATER) return pos.getY() + 1D;
        if (state.getBlock() instanceof BlockLiquid) {
            return pos.getY() + 1D - BlockLiquid.getLiquidHeightPercent(state.getValue(BlockLiquid.LEVEL));
        }
        if (state.getBlock() instanceof IFluidBlock fluid) {
            return pos.getY() + Math.abs(fluid.getFilledPercentage(world, pos));
        }
        return pos.getY() + 1D;
    }

    public static boolean isDry(World world, double x, double y, double z) {
        return containsPoint(world, x, y, z, false);
    }

    public static boolean isWorldWaterMasked(World world, double x, double y, double z) {
        return containsPoint(world, x, y, z, true);
    }

    private static boolean containsPoint(World world, double x, double y, double z, boolean worldWater) {
        if (ShipChunkAllocator.isBlockInShipyard(new BlockPos(x, y, z))) return false;
        IPhysObjectWorld manager = ValkyrienUtils.getPhysObjWorld(world);
        if (manager == null) return false;
        AxisAlignedBB point = new AxisAlignedBB(x, y, z, x, y, z).grow(0.001D);
        for (PhysicsObject ship : manager.getPhysObjectsInAABB(point)) {
            Vector3d local = new Vector3d(x, y, z);
            ship.getShipTransform().transformPosition(local, TransformType.GLOBAL_TO_SUBSPACE);
            Snapshot snapshot = ship.getWaterVolume().getSnapshot();
            if (worldWater && snapshot.isMasked(local.x, local.y, local.z)) return true;
            if (!worldWater && snapshot.isDry(local.x, local.y, local.z)) return true;
        }
        return false;
    }

    /**
     * Masks ocean water inside ship interiors, including cells occupied by retained ship water.
     * */
    public static boolean isWorldWaterMasked(World world, AxisAlignedBB bounds, BlockPos cell) {
        AxisAlignedBB cellBounds = new AxisAlignedBB(cell);
        if (!bounds.intersects(cellBounds)) return false;
        AxisAlignedBB overlap = bounds.intersect(cellBounds);
        double inset = Math.min(0.000001D, Math.min(overlap.maxX - overlap.minX,
                Math.min(overlap.maxY - overlap.minY, overlap.maxZ - overlap.minZ)) * 0.25D);
        overlap = overlap.shrink(inset);
        if (ShipChunkAllocator.isBlockInShipyard(cell)) return false;
        IPhysObjectWorld manager = ValkyrienUtils.getPhysObjWorld(world);
        if (manager == null) return false;
        for (PhysicsObject ship : manager.getPhysObjectsInAABB(overlap)) {
            AxisAlignedBB local = new TransformedAABB(overlap, ship.getShipTransform(), TransformType.GLOBAL_TO_SUBSPACE).getEnclosedAABB();
            if (ship.getWaterVolume().getSnapshot().contains(local)) return true;
        }
        return false;
    }

    public record Snapshot(BlockPos origin, int sizeX, int sizeY, int sizeZ, BitSet dry, BitSet air, BitSet mask) {
        public boolean isDry(double x, double y, double z) {
            return this.containsPoint(x, y, z, this.dry);
        }

        public boolean isMasked(double x, double y, double z) {
            return this.containsPoint(x, y, z, this.mask);
        }

        private boolean containsPoint(double x, double y, double z, BitSet cells) {
            int localX = (int) Math.floor(x) - this.origin.getX();
            int localY = (int) Math.floor(y) - this.origin.getY();
            int localZ = (int) Math.floor(z) - this.origin.getZ();
            if (localX < 0 || localX >= this.sizeX || localY < 0 || localY >= this.sizeY || localZ < 0 || localZ >= this.sizeZ) return false;
            return cells.get(localX + this.sizeX * (localY + this.sizeY * localZ));
        }

        public boolean contains(AxisAlignedBB bounds) {
            if (bounds.minX < this.origin.getX() || bounds.maxX > this.origin.getX() + this.sizeX
                    || bounds.minY < this.origin.getY() || bounds.maxY > this.origin.getY() + this.sizeY
                    || bounds.minZ < this.origin.getZ() || bounds.maxZ > this.origin.getZ() + this.sizeZ) return false;

            for (int z = (int) Math.floor(bounds.minZ); z < Math.ceil(bounds.maxZ); z++) {
                for (int y = (int) Math.floor(bounds.minY); y < Math.ceil(bounds.maxY); y++) {
                    for (int x = (int) Math.floor(bounds.minX); x < Math.ceil(bounds.maxX); x++) {
                        if (!this.isMasked(x, y, z)) return false;
                    }
                }
            }
            return true;
        }

    }
}
