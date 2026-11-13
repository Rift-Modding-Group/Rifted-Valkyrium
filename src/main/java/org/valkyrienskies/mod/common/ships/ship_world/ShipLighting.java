package org.valkyrienskies.mod.common.ships.ship_world;

import gnu.trove.list.array.TLongArrayList;
import gnu.trove.map.hash.TLongObjectHashMap;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.compat.WorldHeightCompat;

import java.util.BitSet;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * World-owned skylight work. All access happens on that world's game thread.
 * */
public class ShipLighting {
    private final Map<Long, BitSet> pendingColumns = new LinkedHashMap<>();

    public void enqueueColumn(int x, int z, int startY, int endY) {
        int minY = Math.clamp(startY, 0, 256);
        int maxY = Math.clamp(endY, 0, 256);
        if (minY >= maxY) return;

        this.pendingColumns.computeIfAbsent(ChunkPos.asLong(x, z), ignored -> new BitSet(256)).set(minY, maxY);
    }

    public void process(@NotNull Consumer<BlockPos> checkSkyLight, @NotNull LongSupplier nanoTime) {
        // Yield between vanilla light checks; an individual flood fill must finish on this thread.
        long started = nanoTime.getAsLong();
        for (int checked = 0; checked < 64 && !this.pendingColumns.isEmpty(); checked++) {
            if (nanoTime.getAsLong() - started >= 2_000_000L) break;

            Iterator<Map.Entry<Long, BitSet>> iterator = this.pendingColumns.entrySet().iterator();
            Map.Entry<Long, BitSet> entry = iterator.next();
            long column = entry.getKey();
            BitSet heights = entry.getValue();
            int y = heights.nextSetBit(0);
            heights.clear(y);
            if (heights.isEmpty()) iterator.remove();

            // Consume before invoking Minecraft: callbacks may enqueue more work for this column.
            checkSkyLight.accept(new BlockPos((int) column, y, (int) (column >> 32)));
        }
    }

    //-----static helpers from here on out-----
    /**
     * Initializes newly installed ship chunks with vertical skylight and a shared propagation
     * queue. The claimed empty margin contains all horizontal light spread.
     */
    public static void initializeShipSkylight(@NotNull World world, @NotNull Collection<Chunk> chunks) {
        rebuildSkylight(world, chunks, false);
    }

    /**
     * Rebuilds changed world chunks together with their loaded margin, preserving lighting
     * from outside that region without generating neighboring terrain.
     */
    @NotNull
    public static Collection<Chunk> relightWorldChunks(@NotNull World world, @NotNull Collection<Chunk> changedChunks) {
        TLongObjectHashMap<Chunk> affectedChunks = new TLongObjectHashMap<>();
        for (Chunk chunk : changedChunks) {
            affectedChunks.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    Chunk neighbor = world.getChunkProvider().getLoadedChunk(chunk.x + dx, chunk.z + dz);
                    if (neighbor != null) affectedChunks.put(ChunkPos.asLong(neighbor.x, neighbor.z), neighbor);
                }
            }
        }
        rebuildSkylight(world, affectedChunks.valueCollection(), true);
        return affectedChunks.valueCollection();
    }

    private static void rebuildSkylight(@NotNull World world, @NotNull Collection<Chunk> chunks, boolean existingWorld) {
        int worldMinY = WorldHeightCompat.getMinimumY(world);
        int worldMaxY = world.getHeight();
        if (existingWorld) {
            for (Chunk chunk : chunks) {
                Arrays.fill(chunk.getHeightMap(), worldMinY);
                for (ExtendedBlockStorage section : chunk.getBlockStorageArray()) {
                    if (section != null && section.getSkyLight() != null) Arrays.fill(section.getSkyLight().getData(), (byte) 0);
                }
            }
        }
        for (Chunk chunk : chunks) chunk.generateSkylightMap();

        if (world.provider.hasSkyLight()) {
            TLongObjectHashMap<Chunk> chunksByPosition = new TLongObjectHashMap<>();
            for (Chunk chunk : chunks) chunksByPosition.put(ChunkPos.asLong(chunk.x, chunk.z), chunk);
            TLongObjectHashMap<Chunk> readableChunks = new TLongObjectHashMap<>(chunksByPosition);
            if (existingWorld) {
                for (Chunk chunk : chunks) {
                    for (EnumFacing direction : EnumFacing.Plane.HORIZONTAL) {
                        int x = chunk.x + direction.getXOffset();
                        int z = chunk.z + direction.getZOffset();
                        long key = ChunkPos.asLong(x, z);
                        if (readableChunks.containsKey(key)) continue;
                        Chunk neighbor = world.getChunkProvider().getLoadedChunk(x, z);
                        if (neighbor != null) readableChunks.put(key, neighbor);
                    }
                }
            }
            TLongArrayList lightQueue = new TLongArrayList();
            BlockPos.MutableBlockPos position = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos neighborPosition = new BlockPos.MutableBlockPos();
            EnumFacing[] directions = EnumFacing.values();
            for (Chunk chunk : chunks) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        int height = chunk.getHeightValue(x, z);
                        if (height == worldMinY) {
                            int floorIndex = WorldHeightCompat.getStorageIndex(world, worldMinY);
                            ExtendedBlockStorage floorStorage = chunk.getBlockStorageArray()[floorIndex];
                            if (existingWorld && floorStorage != null) {
                                floorStorage.setSkyLight(x, worldMinY & 15, z, 15);
                            }
                            continue;
                        }

                        int worldX = (chunk.x << 4) + x;
                        int worldZ = (chunk.z << 4) + z;
                        Chunk west = chunk;
                        Chunk east = chunk;
                        Chunk north = chunk;
                        Chunk south = chunk;
                        if (x == 0) west = readableChunks.get(ChunkPos.asLong(chunk.x - 1, chunk.z));
                        if (x == 15) east = readableChunks.get(ChunkPos.asLong(chunk.x + 1, chunk.z));
                        if (z == 0) north = readableChunks.get(ChunkPos.asLong(chunk.x, chunk.z - 1));
                        if (z == 15) south = readableChunks.get(ChunkPos.asLong(chunk.x, chunk.z + 1));
                        for (int y = Math.min(height, worldMaxY) - 1; y >= worldMinY; y--) {
                            position.setPos(worldX, y, worldZ);
                            int light = chunk.getLightFor(EnumSkyBlock.SKY, position);
                            if (light >= 14) continue;

                            IBlockState state = chunk.getBlockState(position);
                            int opacity = Math.max(1, state.getLightOpacity(world, position));
                            if (light + opacity >= 15) continue;

                            for (EnumFacing direction : directions) {
                                int neighborY = y + direction.getYOffset();
                                if (neighborY < worldMinY || neighborY >= worldMaxY) continue;
                                Chunk neighbor = switch (direction) {
                                    case WEST -> west;
                                    case EAST -> east;
                                    case NORTH -> north;
                                    case SOUTH -> south;
                                    default -> chunk;
                                };
                                if (neighbor == null) continue;
                                neighborPosition.setPos(worldX + direction.getXOffset(), neighborY, worldZ + direction.getZOffset());
                                light = Math.max(light, neighbor.getLightFor(EnumSkyBlock.SKY, neighborPosition) - opacity);
                            }
                            if (light > chunk.getLightFor(EnumSkyBlock.SKY, position)) {
                                initializeLightStorage(chunk, y >> 4, true);
                                chunk.getBlockStorageArray()[WorldHeightCompat.getStorageIndex(world, y)].setSkyLight(x, y & 15, z, light);
                                lightQueue.add(position.toLong());
                                world.notifyLightSet(position.toImmutable());
                            }
                        }
                    }
                }
            }
            for (int next = 0; next < lightQueue.size(); next++) {
                position.setPos(BlockPos.fromLong(lightQueue.get(next)));
                Chunk chunk = chunksByPosition.get(ChunkPos.asLong(position.getX() >> 4, position.getZ() >> 4));
                int light = chunk.getLightFor(EnumSkyBlock.SKY, position);
                if (light <= 1) continue;

                for (EnumFacing direction : directions) {
                    neighborPosition.setPos(position.getX() + direction.getXOffset(), position.getY() + direction.getYOffset(), position.getZ() + direction.getZOffset());
                    int neighborY = neighborPosition.getY();
                    if (neighborY < worldMinY || neighborY >= worldMaxY) continue;
                    Chunk neighbor = chunk;
                    if (neighborPosition.getX() >> 4 != chunk.x || neighborPosition.getZ() >> 4 != chunk.z) {
                        neighbor = chunksByPosition.get(ChunkPos.asLong(neighborPosition.getX() >> 4, neighborPosition.getZ() >> 4));
                    }
                    if (neighbor == null) continue;
                    int previousLight = neighbor.getLightFor(EnumSkyBlock.SKY, neighborPosition);
                    if (previousLight >= light - 1) continue;

                    IBlockState state = neighbor.getBlockState(neighborPosition);
                    int propagatedLight = light - Math.max(1, state.getLightOpacity(world, neighborPosition));
                    if (propagatedLight <= previousLight) continue;

                    initializeLightStorage(neighbor, neighborY >> 4, true);
                    neighbor.getBlockStorageArray()[WorldHeightCompat.getStorageIndex(world, neighborY)].setSkyLight(neighborPosition.getX() & 15, neighborY & 15, neighborPosition.getZ() & 15, propagatedLight);
                    lightQueue.add(neighborPosition.toLong());
                    world.notifyLightSet(neighborPosition.toImmutable());
                }
            }
        }
        for (Chunk chunk : chunks) {
            if (!existingWorld) {
                chunk.setTerrainPopulated(true);
                chunk.setLightPopulated(true);
            }
            chunk.markDirty();
        }
    }

    /**
     * Preserve the implicit light values of an empty section without relighting the whole chunk.
     * */
    public static void initializeLightStorage(@NotNull Chunk chunk, int section, boolean hasSkyLight) {
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        int storageIndex = WorldHeightCompat.getStorageIndex(chunk.getWorld(), section << 4);
        if (storageIndex < 0 || storageIndex >= storage.length || storage[storageIndex] != null) return;

        ExtendedBlockStorage created = new ExtendedBlockStorage(section << 4, hasSkyLight);
        if (hasSkyLight) {
            int minY = section << 4;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int skyStart = Math.clamp(chunk.getHeightValue(x, z) - minY, 0, 16);
                    for (int y = skyStart; y < 16; y++) created.setSkyLight(x, y, z, 15);
                }
            }
        }
        storage[storageIndex] = created;
    }
}
