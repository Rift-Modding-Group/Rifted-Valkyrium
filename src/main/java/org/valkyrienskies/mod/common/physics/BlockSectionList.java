package org.valkyrienskies.mod.common.physics;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.entity.EntityMountable;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * This class stores the block sections that a ship is going to collide with.
 * */
public class BlockSectionList {
    private static final int BLOCK_SECTION_CACHE_RESCAN_INTERVAL_TICKS = 20;
    private static final int BLOCK_SECTION_CACHE_PADDING_BLOCKS = 16;
    private static final int BLOCK_SECTION_CACHE_HYSTERESIS_BLOCKS = 4;
    private static final Map<BlockSection.Key, Set<BlockSectionList>> CACHES_BY_SECTION = new ConcurrentHashMap<>();

    @Nullable
    private World cachedBlockSectionWorld;
    @Nullable
    private BlockSection.Range cachedBlockSectionRange;
    private int blockSectionCacheAge;
    private final Map<BlockSection.Key, BlockSection> cachedBlockSections = new HashMap<>();
    private final Set<BlockSection.Key> cachedEmptyBlockSections = new HashSet<>();
    private final Queue<BlockSection.Key> dirtyBlockSections = new ConcurrentLinkedQueue<>();
    private final Set<BlockSection.Key> registeredBlockSections = new HashSet<>();
    @NotNull
    private List<BlockSection> blockSections = List.of();

    public void onUpdate(@NotNull PhysicsObject physicsObject) {
        AxisAlignedBB shipAabb = physicsObject.getPhysicsTransformAABB();
        if (shipAabb != null) {
            World world = physicsObject.getWorld();
            List<BlockSection> blockSections = this.updateBlockSectionCache(world, shipAabb);
            this.blockSections = List.copyOf(blockSections);
        }
        else  {
            this.clearBlockSectionCache();
            this.blockSections = List.of();
        }
    }

    public void close() {
        this.clearBlockSectionCache();
        this.dirtyBlockSections.clear();
        this.blockSections = List.of();
    }

    /**
     * Figures out which block sections the ship needs and keeps the cache up to date.
     * If the chunks are not loaded, it just clears the old cache.
     */
    @NotNull
    private List<BlockSection> updateBlockSectionCache(@NotNull World world, @NotNull AxisAlignedBB shipAabb) {
        BlockPos shipMin = new BlockPos(
                (int) Math.floor(shipAabb.minX),
                Math.max(0, (int) Math.floor(shipAabb.minY)),
                (int) Math.floor(shipAabb.minZ)
        );
        BlockPos shipMax = new BlockPos(
                (int) Math.ceil(shipAabb.maxX),
                Math.min(world.getHeight() - 1, (int) Math.ceil(shipAabb.maxY)),
                (int) Math.ceil(shipAabb.maxZ)
        );

        BlockSection.Range exactRange = BlockSection.Range.fromBlockCorners(shipMin, shipMax);
        if (!this.areChunksLoaded(world, exactRange)) {
            this.clearBlockSectionCache();
            return List.of();
        }

        BlockSection.Range hysteresisRange = this.createExpandedRange(world, shipMin, shipMax, BLOCK_SECTION_CACHE_HYSTERESIS_BLOCKS);
        BlockSection.Range paddedRange = this.createExpandedRange(world, shipMin, shipMax, BLOCK_SECTION_CACHE_PADDING_BLOCKS);

        boolean sameWorld = this.cachedBlockSectionWorld == world;
        boolean useCachedRange = sameWorld && this.cachedBlockSectionRange != null && this.cachedBlockSectionRange.contains(hysteresisRange);
        BlockSection.Range nextRange = useCachedRange ? this.cachedBlockSectionRange : paddedRange;
        if (!this.areChunksLoaded(world, nextRange)) nextRange = exactRange;

        List<BlockSection.Key> nextRegisteredSections = nextRange.keys(world);
        boolean sameRange = sameWorld && nextRange.equals(this.cachedBlockSectionRange);
        boolean forceRescan = !sameWorld || (sameRange && this.blockSectionCacheAge >= BLOCK_SECTION_CACHE_RESCAN_INTERVAL_TICKS);
        int nextCacheAge = sameRange && !forceRescan ? this.blockSectionCacheAge + 1 : 0;

        Map<BlockSection.Key, BlockSection> previousSections = new HashMap<>(this.cachedBlockSections);
        Set<BlockSection.Key> previousEmptySections = new HashSet<>(this.cachedEmptyBlockSections);
        Set<BlockSection.Key> dirtySections = this.drainDirtyBlockSections();
        Map<BlockSection.Key, BlockSection> nextCachedSections = new HashMap<>();
        Set<BlockSection.Key> nextCachedEmptySections = new HashSet<>();
        List<BlockSection> nextBlockSections = new ArrayList<>();

        ChunkCache chunkCache = null;
        BlockPos cacheMin = new BlockPos(
                nextRange.minX() << 4,
                Math.max(0, nextRange.minY() << 4),
                nextRange.minZ() << 4
        );
        BlockPos cacheMax = new BlockPos(
                (nextRange.maxX() << 4) + 15,
                Math.min(world.getHeight() - 1, (nextRange.maxY() << 4) + 15),
                (nextRange.maxZ() << 4) + 15
        );

        for (BlockSection.Key sectionKey : nextRegisteredSections) {
            BlockSection cachedSection = previousSections.get(sectionKey);
            boolean knownEmpty = previousEmptySections.contains(sectionKey);
            boolean shouldRescan = forceRescan || dirtySections.contains(sectionKey) || (cachedSection == null && !knownEmpty);

            if (shouldRescan) {
                if (chunkCache == null) chunkCache = new ChunkCache(world, cacheMin, cacheMax, 0);
                BlockSection blockSection = this.createBlockSection(world, chunkCache, sectionKey);
                if (blockSection == null) {
                    nextCachedEmptySections.add(sectionKey);
                }
                else {
                    nextCachedSections.put(sectionKey, blockSection);
                    nextBlockSections.add(blockSection);
                }
            }
            else {
                if (cachedSection != null) {
                    nextCachedSections.put(sectionKey, cachedSection);
                    nextBlockSections.add(cachedSection);
                }
                else nextCachedEmptySections.add(sectionKey);
            }
        }

        this.updateRegisteredBlockSections(nextRegisteredSections);
        this.cachedBlockSectionWorld = world;
        this.cachedBlockSectionRange = nextRange;
        this.blockSectionCacheAge = nextCacheAge;
        this.cachedBlockSections.clear();
        this.cachedBlockSections.putAll(nextCachedSections);
        this.cachedEmptyBlockSections.clear();
        this.cachedEmptyBlockSections.addAll(nextCachedEmptySections);
        return nextBlockSections;
    }

    /**
     * Clears the block section cache and stops watching its old sections.
     * Dirty section updates stay queued unless the whole cache is being closed.
     */
    private void clearBlockSectionCache() {
        this.updateRegisteredBlockSections(List.of());
        this.cachedBlockSections.clear();
        this.cachedEmptyBlockSections.clear();
        this.cachedBlockSectionWorld = null;
        this.cachedBlockSectionRange = null;
        this.blockSectionCacheAge = 0;
    }

    /**
     * Grows a block range while keeping its Y values inside the world.
     */
    @NotNull
    private BlockSection.Range createExpandedRange(@NotNull World world, @NotNull BlockPos min, @NotNull BlockPos max, int grow) {
        BlockPos expandedMin = new BlockPos(
                min.getX() - grow,
                Math.max(0, min.getY() - grow),
                min.getZ() - grow
        );
        BlockPos expandedMax = new BlockPos(
                max.getX() + grow,
                Math.min(world.getHeight() - 1, max.getY() + grow),
                max.getZ() + grow
        );
        return BlockSection.Range.fromBlockCorners(expandedMin, expandedMax);
    }

    //-----block section manipulation-----
    @NotNull
    private Set<BlockSection.Key> drainDirtyBlockSections() {
        Set<BlockSection.Key> dirtySections = new HashSet<>();
        BlockSection.Key dirtySection;
        while ((dirtySection = this.dirtyBlockSections.poll()) != null) {
            dirtySections.add(dirtySection);
        }
        return dirtySections;
    }

    private void updateRegisteredBlockSections(@NotNull List<BlockSection.Key> nextBlockSections) {
        Set<BlockSection.Key> nextBlockSectionSet = new HashSet<>(nextBlockSections);
        for (BlockSection.Key sectionKey : new ArrayList<>(this.registeredBlockSections)) {
            if (nextBlockSectionSet.contains(sectionKey)) continue;
            this.removeBlockSectionRegistration(sectionKey);
            this.registeredBlockSections.remove(sectionKey);
        }

        for (BlockSection.Key sectionKey : nextBlockSections) {
            if (!this.registeredBlockSections.add(sectionKey)) continue;
            CACHES_BY_SECTION.computeIfAbsent(sectionKey, ignored -> Collections.newSetFromMap(new ConcurrentHashMap<>())).add(this);
        }
    }

    private void removeBlockSectionRegistration(@NotNull BlockSection.Key sectionKey) {
        Set<BlockSectionList> sectionCaches = CACHES_BY_SECTION.get(sectionKey);
        if (sectionCaches == null) return;

        sectionCaches.remove(this);
        if (sectionCaches.isEmpty()) CACHES_BY_SECTION.remove(sectionKey, sectionCaches);
    }

    @Nullable
    private BlockSection createBlockSection(World world, ChunkCache chunkCache, BlockSection.Key sectionKey) {
        BlockPos.MutableBlockPos mutablePos = new BlockPos.MutableBlockPos();
        BlockSection.Builder builder = new BlockSection.Builder(
                world,
                sectionKey.sectionX(),
                sectionKey.sectionY(),
                sectionKey.sectionZ()
        );
        int minX = sectionKey.sectionX() << 4;
        int minY = Math.max(0, sectionKey.sectionY() << 4);
        int minZ = sectionKey.sectionZ() << 4;
        int maxX = minX + 15;
        int maxY = Math.min(world.getHeight() - 1, (sectionKey.sectionY() << 4) + 15);
        int maxZ = minZ + 15;
        if (maxY < minY) return null;

        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) {
                for (int y = minY; y <= maxY; y++) {
                    mutablePos.setPos(x, y, z);
                    IBlockState state = chunkCache.getBlockState(mutablePos);
                    Material material = state.getMaterial();
                    boolean liquid = PhysicsUtils.isLiquid(state);
                    if (material.equals(Material.AIR) || (!liquid && !material.blocksMovement())) continue;
                    builder.addBlock(mutablePos, state, liquid);
                }
            }
        }

        return builder.isEmpty() ? null : builder.build();
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    private boolean areChunksLoaded(World world, BlockSection.Range sectionRange) {
        ChunkProviderServer serverChunkProvider = (ChunkProviderServer) world.getChunkProvider();
        for (int chunkX = sectionRange.minX(); chunkX <= sectionRange.maxX(); chunkX++) {
            for (int chunkZ = sectionRange.minZ(); chunkZ <= sectionRange.maxZ(); chunkZ++) {
                if (!serverChunkProvider.chunkExists(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    @NotNull
    public List<BlockSection> getBlockSections() {
        return this.blockSections;
    }

    //-----stuff for editing caches by section map-----
    public static void invalidateBlockSectionAt(@NotNull World world, @NotNull BlockPos pos) {
        BlockSection.Key sectionKey = BlockSection.Key.fromBlockPos(world, pos);
        Set<BlockSectionList> sectionCaches = CACHES_BY_SECTION.get(sectionKey);
        if (sectionCaches == null) return;

        for (BlockSectionList collideWith : new ArrayList<>(sectionCaches)) {
            collideWith.dirtyBlockSections.add(sectionKey);
        }
    }

    public static void invalidateBlockSectionsInChunk(@NotNull World world, int chunkX, int chunkZ) {
        for (Map.Entry<BlockSection.Key, Set<BlockSectionList>> entry : CACHES_BY_SECTION.entrySet()) {
            BlockSection.Key sectionKey = entry.getKey();
            if (sectionKey.world() != world || sectionKey.sectionX() != chunkX || sectionKey.sectionZ() != chunkZ) {
                continue;
            }

            for (BlockSectionList collideWith : new ArrayList<>(entry.getValue())) {
                collideWith.dirtyBlockSections.add(sectionKey);
            }
        }
    }

    public static void clearBlockSectionRegistrationsForWorld(@NotNull World world) {
        for (Map.Entry<BlockSection.Key, Set<BlockSectionList>> entry : CACHES_BY_SECTION.entrySet()) {
            if (entry.getKey().world() != world) continue;
            for (BlockSectionList collideWith : new ArrayList<>(entry.getValue())) {
                collideWith.close();
            }
        }
    }
}
