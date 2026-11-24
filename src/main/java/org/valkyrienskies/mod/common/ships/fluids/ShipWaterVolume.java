package org.valkyrienskies.mod.common.ships.fluids;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
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
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraft.block.Block;
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
import java.util.ArrayDeque;
import java.util.BitSet;
import java.util.Arrays;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.function.IntUnaryOperator;

/**
 * Ship-local fluid connectivity. Snapshots are published on the game thread for physics and rendering.
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
    private long inflowTicks;
    private boolean volumeLimitWarned;
    private BlockPos origin = BlockPos.ORIGIN;
    private int sizeX;
    private int sizeY;
    private int sizeZ;
    private byte[] openFaces = new byte[0];
    private final BitSet localFluid = new BitSet();
    private final Map<IBlockState, BitSet> localFluids = new HashMap<>();
    private IBlockState[] cellStates = new IBlockState[0];
    private final BitSet air = new BitSet();
    private BitSet interior = new BitSet();
    private final BitSet changedCells = new BitSet();
    private final Map<IBlockState, BitSet> acceptsFluids = new HashMap<>();
    private final BitSet passages = new BitSet();
    private final Long2ObjectOpenHashMap<FluidSample> fluidSamples = new Long2ObjectOpenHashMap<>();

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
                    ValkyrienSkiesMod.LOGGER.warn("Fluid sealing skipped for ship {}: hull bounds contain {} cells (limit {}).",
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
            this.localFluids.clear();
            this.cellStates = new IBlockState[(int) volume];
            this.air.clear();
            this.acceptsFluids.clear();
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
                for (BitSet cells : this.localFluids.values()) cells.clear(index);
                this.cellStates[index] = null;
                this.air.set(index);
                for (BitSet cells : this.acceptsFluids.values()) cells.clear(index);
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
                    this.cellStates[index] = state;
                    this.localFluid.set(index, isFluid(fluid));
                    if (this.localFluid.get(index)) this.localFluids.computeIfAbsent(flowingState(fluid), ignored -> new BitSet()).set(index);
                    faces = 0;
                    if (!(actual.getBlock() instanceof BlockDoor) || actual.getValue(BlockDoor.OPEN)) {
                        for (EnumFacing side : EnumFacing.values()) {
                            boolean open;
                            if (fluidlogged) open = FluidloggedAPICompat.canFluidFlow(world, pos, actual, side);
                            else open = actual.getBlockFaceShape(world, pos, side) != BlockFaceShape.SOLID;
                            if (open) faces |= (byte) (1 << side.getIndex());
                        }
                    }
                    if (!world.isRemote && faces != 0) {
                        for (Map.Entry<IBlockState, BitSet> entry : this.acceptsFluids.entrySet()) {
                            boolean accepts = state.getMaterial() == Material.AIR && !this.localFluid.get(index);
                            if (!accepts && fluidlogged && !this.localFluid.get(index)) {
                                accepts = FluidloggedAPICompat.canAdmitFluid(world, pos, state, entry.getKey());
                            }
                            entry.getValue().set(index, accepts);
                        }
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
        this.fluidSamples.clear();
        Map<IBlockState, byte[]> fluidFaces = new LinkedHashMap<>();
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
                double faceTop = center.y + normal.y * 0.5D + verticalExtent - Math.abs(normal.y) * 0.5D;
                for (int endpoint = 0; endpoint < 2; endpoint++) {
                    double sampleY = endpoint == 0 ? faceBottom + 0.0001D : faceTop - 0.0001D;
                    BlockPos samplePos = new BlockPos(sampleX, sampleY, sampleZ);
                    long sample = samplePos.toLong();
                    if (!this.fluidSamples.containsKey(sample)) this.fluidSamples.put(sample, fluidSample(world, samplePos));
                    FluidSample fluid = this.fluidSamples.get(sample);
                    if (fluid == null || !fluid.contains(sampleY)) continue;
                    wet |= 1 << side.getIndex();
                    if (!world.isRemote) {
                        byte[] faces = fluidFaces.computeIfAbsent(flowingState(fluid.state()), ignored -> new byte[this.openFaces.length]);
                        faces[index] |= (byte) (1 << side.getIndex());
                    }
                }
            }
            wetFaces[index] = (byte) wet;
            return wet;
        }, new BitSet());
        this.inflowTicks++;
        if (!world.isRemote) {
            int placedCount = 0;
            boolean fluidlogged = Loader.isModLoaded("fluidlogged_api");
            for (Map.Entry<IBlockState, byte[]> entry : fluidFaces.entrySet()) {
                IBlockState incoming = entry.getKey();
                if (this.inflowTicks % Math.max(1, incoming.getBlock().tickRate(world)) != 0) continue;
                BitSet accepted = this.acceptsFluids.get(incoming);
                if (accepted == null) {
                    accepted = new BitSet(this.openFaces.length);
                    BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                    for (int index = 0; index < this.cellStates.length; index++) {
                        IBlockState state = this.cellStates[index];
                        if (state == null || this.openFaces[index] == 0 || this.localFluid.get(index)) continue;
                        pos.setPos(this.origin.getX() + index % this.sizeX, this.origin.getY() + index / this.sizeX % this.sizeY,
                                this.origin.getZ() + index / (this.sizeX * this.sizeY));
                        boolean accepts = state.getMaterial() == Material.AIR;
                        if (!accepts && fluidlogged) accepts = FluidloggedAPICompat.canAdmitFluid(world, pos, state, incoming);
                        if (accepts) accepted.set(index);
                    }
                    this.acceptsFluids.put(incoming, accepted);
                }
                BitSet fillable = (BitSet) accepted.clone();
                fillable.andNot(dry);
                fillable.andNot(this.localFluid);
                fillable.and(this.interior);
                BitSet openPassages = (BitSet) this.passages.clone();
                openPassages.andNot(dry);
                openPassages.andNot(this.localFluid);
                openPassages.andNot(fillable);
                BitSet supplied = this.localFluids.getOrDefault(incoming, new BitSet());
                BitSet intake = findInflowCells(this.sizeX, this.sizeY, this.sizeZ, this.openFaces, entry.getValue(),
                        fillable, openPassages, supplied);
                for (int index = intake.nextSetBit(0); index >= 0 && placedCount < 64; index = intake.nextSetBit(index + 1)) {
                    if (this.localFluid.get(index)) continue;
                    BlockPos pos = this.origin.add(index % this.sizeX, index / this.sizeX % this.sizeY, index / (this.sizeX * this.sizeY));
                    boolean placed;
                    if (fluidlogged) placed = FluidloggedAPICompat.admitFluid(world, pos, incoming);
                    else placed = world.getBlockState(pos).getMaterial() == Material.AIR && world.setBlockState(pos, incoming, 3);
                    if (!placed) continue;
                    placedCount++;
                    this.localFluid.set(index);
                    this.localFluids.computeIfAbsent(incoming, ignored -> new BitSet()).set(index);
                    this.air.clear(index);
                    for (BitSet cells : this.acceptsFluids.values()) cells.clear(index);
                    this.passages.clear(index);
                    this.markFluidDirty(pos);
                }
                if (placedCount >= 64) break;
            }
        }
        dry.or(this.interior);
        dry.andNot(this.localFluid);
        BitSet mask = (BitSet) dry.clone();
        mask.or(this.localFluid);
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
     * Hull bounds follow non-fluid blocks, so escaping fluids cannot extend the ship.
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

    /** Finds the next fluid front, allowing fluids to pass through open doors without replacing them. */
    public static BitSet findInflowCells(
            int sizeX, int sizeY, int sizeZ, byte[] openFaces, byte[] wetFaces,
            BitSet fillable, BitSet passages, BitSet water
    ) {
        int volume = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        if (openFaces.length != volume || wetFaces.length != volume) throw new IllegalArgumentException("Invalid fluid grid");
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
        if (openFaces.length != volume) throw new IllegalArgumentException("Invalid fluid grid");
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
     * Follows continuous open faces touching fluids, leaving openings above the fluid surface dry.
     */
    public static BitSet findDryCells(int sizeX, int sizeY, int sizeZ, byte[] openFaces, IntUnaryOperator wetFaces, BitSet localFluid) {
        int volume = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        if (openFaces.length != volume) throw new IllegalArgumentException("Invalid fluid grid");
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

    public static boolean isFluid(@Nullable IBlockState state) {
        return state != null && (state.getMaterial().isLiquid() || state.getBlock() instanceof IFluidBlock);
    }

    public static boolean isFluidMaterial(Material material) {
        if (material.isLiquid()) return true;
        for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
            Block block = fluid.getBlock();
            if (block != null && block.getDefaultState().getMaterial() == material) return true;
        }
        return false;
    }

    public static IBlockState flowingState(IBlockState state) {
        if (state.getBlock() instanceof BlockLiquid) {
            if (state.getMaterial() == Material.WATER) return Blocks.FLOWING_WATER.getDefaultState();
            if (state.getMaterial() == Material.LAVA) return Blocks.FLOWING_LAVA.getDefaultState();
        }
        return state.getBlock().getDefaultState();
    }

    @Nullable
    public static FluidSample fluidSample(World world, BlockPos pos) {
        if (world.isOutsideBuildHeight(pos) || !world.isBlockLoaded(pos, false)) return null;
        if (Loader.isModLoaded("fluidlogged_api")) return FluidloggedAPICompat.getFluidSample(world, pos);
        IBlockState state = world.getBlockState(pos);
        if (!isFluid(state)) return null;
        double filled = 1D;
        if (state.getBlock() instanceof BlockLiquid) {
            IBlockState above = world.getBlockState(pos.up());
            if (above.getMaterial() != state.getMaterial()) filled = 1D - BlockLiquid.getLiquidHeightPercent(state.getValue(BlockLiquid.LEVEL));
        }
        else if (state.getBlock() instanceof IFluidBlock fluid) filled = fluid.getFilledPercentage(world, pos);
        double amount = Math.clamp(Math.abs(filled), 0D, 1D);
        if (filled < 0D) return new FluidSample(state, pos.getY() + 1D - amount, pos.getY() + 1D);
        return new FluidSample(state, pos.getY(), pos.getY() + amount);
    }

    public record FluidSample(IBlockState state, double minY, double maxY) {
        public boolean contains(double y) {
            return y >= this.minY && y < this.maxY;
        }
    }

    /**
     * Returns the unmasked fluid surface without loading terrain chunks.
     * */
    public static double fluidHeight(World world, double x, double y, double z) {
        FluidSample sample = fluidSample(world, new BlockPos(x, y, z));
        if (sample == null || !sample.contains(y)) return y;
        return sample.maxY();
    }

    public static boolean isDry(World world, double x, double y, double z) {
        return containsPoint(world, x, y, z, false);
    }

    public static boolean isWorldFluidMasked(World world, double x, double y, double z) {
        return containsPoint(world, x, y, z, true);
    }

    private static boolean containsPoint(World world, double x, double y, double z, boolean worldFluid) {
        if (ShipChunkAllocator.isBlockInShipyard(new BlockPos(x, y, z))) return false;
        IPhysObjectWorld manager = ValkyrienUtils.getPhysObjWorld(world);
        if (manager == null) return false;
        AxisAlignedBB point = new AxisAlignedBB(x, y, z, x, y, z).grow(0.001D);
        for (PhysicsObject ship : manager.getPhysObjectsInAABB(point)) {
            Vector3d local = new Vector3d(x, y, z);
            ship.getShipTransform().transformPosition(local, TransformType.GLOBAL_TO_SUBSPACE);
            Snapshot snapshot = ship.getWaterVolume().getSnapshot();
            if (worldFluid && snapshot.isMasked(local.x, local.y, local.z)) return true;
            if (!worldFluid && snapshot.isDry(local.x, local.y, local.z)) return true;
        }
        return false;
    }

    /**
     * Masks world fluids inside ship interiors, including cells occupied by retained ship fluids.
     * */
    public static boolean isWorldFluidMasked(World world, AxisAlignedBB bounds, BlockPos cell) {
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
