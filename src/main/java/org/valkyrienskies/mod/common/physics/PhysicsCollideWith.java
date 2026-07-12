package org.valkyrienskies.mod.common.physics;

import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderServer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.entity.EntityMountable;
import org.valkyrienskies.mod.common.physics.physx.collision.PhysXBlockSectionCollider;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class stores the block sections and entities that a ship is going to collide with.
 * */
public class PhysicsCollideWith {
    public static final int MAX_ENTITIES = 512;
    public static final double ENTITY_SCAN_GROW = 2D;
    private static final int BLOCK_SECTION_CACHE_RESCAN_INTERVAL_TICKS = 20;
    private static final int BLOCK_SECTION_CACHE_PADDING_BLOCKS = 16;
    private static final int BLOCK_SECTION_CACHE_HYSTERESIS_BLOCKS = 4;
    private static final Map<BlockSection.Key, Set<PhysicsCollideWith>> CACHES_BY_SECTION = new ConcurrentHashMap<>();

    private final List<Entity> entities = new ArrayList<>();

    private World cachedBlockSectionWorld;
    private BlockSection.Range cachedBlockSectionRange;
    private int blockSectionCacheAge;
    private final Map<BlockSection.Key, BlockSection> cachedBlockSections = new HashMap<>();
    private final Set<BlockSection.Key> cachedEmptyBlockSections = new HashSet<>();
    private final Set<BlockSection.Key> dirtyBlockSections = new HashSet<>();
    private final Set<BlockSection.Key> registeredBlockSections = new HashSet<>();
    private final List<BlockSection> blockSections = new ArrayList<>();

    public void onUpdate(PhysicsObject physicsObject) {
        World nextBlockSectionWorld = null;
        BlockSection.Range nextBlockSectionRange = null;
        int nextBlockSectionCacheAge = 0;
        List<Entity> nextEntities = new ArrayList<>();
        List<BlockSection> nextBlockSections = new ArrayList<>();
        Map<BlockSection.Key, BlockSection> nextCachedBlockSections = new HashMap<>();
        Set<BlockSection.Key> nextCachedEmptyBlockSections = new HashSet<>();
        List<BlockSection.Key> nextRegisteredBlockSections = Collections.emptyList();

        AxisAlignedBB shipAabb = physicsObject.getPhysicsTransformAABB();
        if (shipAabb != null) {
            World world = physicsObject.getWorld();
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

            //---chunk scanning---
            BlockSection.Range exactBlockSectionRange = BlockSection.Range.fromBlockCorners(shipMin, shipMax);
            boolean areSurroundingChunksLoaded = this.areChunksLoaded(world, exactBlockSectionRange);

            //---block section scanning---
            if (areSurroundingChunksLoaded) {
                nextBlockSectionWorld = world;
                BlockSection.Range hysteresisRange = BlockSection.Range.fromBlockCorners(
                        this.growMin(shipMin, BLOCK_SECTION_CACHE_HYSTERESIS_BLOCKS),
                        this.growMax(shipMax, world, BLOCK_SECTION_CACHE_HYSTERESIS_BLOCKS)
                );
                BlockSection.Range paddedRange = BlockSection.Range.fromBlockCorners(
                        this.growMin(shipMin, BLOCK_SECTION_CACHE_PADDING_BLOCKS),
                        this.growMax(shipMax, world, BLOCK_SECTION_CACHE_PADDING_BLOCKS)
                );
                Map<BlockSection.Key, BlockSection> previousBlockSections;
                Set<BlockSection.Key> previousEmptyBlockSections;
                Set<BlockSection.Key> dirtyBlockSections;
                boolean forceSectionRescan;
                synchronized (this) {
                    boolean sameWorld = this.cachedBlockSectionWorld == world;
                    boolean useCachedRange = sameWorld
                            && this.cachedBlockSectionRange != null
                            && this.cachedBlockSectionRange.contains(hysteresisRange);
                    nextBlockSectionRange = useCachedRange ? this.cachedBlockSectionRange : paddedRange;
                    if (!this.areChunksLoaded(world, nextBlockSectionRange)) nextBlockSectionRange = exactBlockSectionRange;
                    nextRegisteredBlockSections = nextBlockSectionRange.keys(world);
                    boolean sameRange = sameWorld && nextBlockSectionRange.equals(this.cachedBlockSectionRange);
                    forceSectionRescan = !sameWorld
                            || (sameRange && this.blockSectionCacheAge >= BLOCK_SECTION_CACHE_RESCAN_INTERVAL_TICKS);
                    nextBlockSectionCacheAge = sameRange && !forceSectionRescan ? this.blockSectionCacheAge + 1 : 0;
                    previousBlockSections = new HashMap<>(this.cachedBlockSections);
                    previousEmptyBlockSections = new HashSet<>(this.cachedEmptyBlockSections);
                    dirtyBlockSections = new HashSet<>(this.dirtyBlockSections);
                }

                ChunkCache chunkCache = null;
                BlockPos cacheMin = new BlockPos(
                        nextBlockSectionRange.minX() << 4,
                        Math.max(0, nextBlockSectionRange.minY() << 4),
                        nextBlockSectionRange.minZ() << 4
                );
                BlockPos cacheMax = new BlockPos(
                        (nextBlockSectionRange.maxX() << 4) + 15,
                        Math.min(world.getHeight() - 1, (nextBlockSectionRange.maxY() << 4) + 15),
                        (nextBlockSectionRange.maxZ() << 4) + 15
                );
                for (BlockSection.Key sectionKey : nextRegisteredBlockSections) {
                    BlockSection cachedSection = previousBlockSections.get(sectionKey);
                    boolean knownEmptySection = previousEmptyBlockSections.contains(sectionKey);
                    boolean shouldRescanSection = forceSectionRescan
                            || dirtyBlockSections.contains(sectionKey)
                            || (cachedSection == null && !knownEmptySection);

                    if (!shouldRescanSection) {
                        if (cachedSection != null) {
                            nextCachedBlockSections.put(sectionKey, cachedSection);
                            nextBlockSections.add(cachedSection);
                        }
                        else nextCachedEmptyBlockSections.add(sectionKey);
                        continue;
                    }

                    if (chunkCache == null) chunkCache = new ChunkCache(world, cacheMin, cacheMax, 0);
                    BlockSection blockSection = this.createBlockSection(world, chunkCache, sectionKey);
                    if (blockSection == null) {
                        nextCachedEmptyBlockSections.add(sectionKey);
                    }
                    else {
                        nextCachedBlockSections.put(sectionKey, blockSection);
                        nextBlockSections.add(blockSection);
                    }
                }
            }

            //---entity scanning---
            nextEntities.addAll(world.getEntitiesWithinAABB(
                    Entity.class, shipAabb.grow(ENTITY_SCAN_GROW),
                    entity -> this.isEntityCollidable(entity, world)
            ));
            if (nextEntities.size() > MAX_ENTITIES) {
                nextEntities.subList(MAX_ENTITIES, nextEntities.size()).clear();
            }
        }

        synchronized (this) {
            this.updateRegisteredBlockSections(nextRegisteredBlockSections);
            this.cachedBlockSectionWorld = nextBlockSectionWorld;
            this.cachedBlockSectionRange = nextBlockSectionRange;
            this.blockSectionCacheAge = nextBlockSectionCacheAge;
            this.cachedBlockSections.clear();
            this.cachedBlockSections.putAll(nextCachedBlockSections);
            this.cachedEmptyBlockSections.clear();
            this.cachedEmptyBlockSections.addAll(nextCachedEmptyBlockSections);
            nextRegisteredBlockSections.forEach(this.dirtyBlockSections::remove);
            this.dirtyBlockSections.retainAll(this.registeredBlockSections);
            this.entities.clear();
            this.entities.addAll(nextEntities);
            this.blockSections.clear();
            this.blockSections.addAll(nextBlockSections);
        }
    }

    public synchronized void close() {
        this.updateRegisteredBlockSections(Collections.emptyList());
        this.cachedBlockSections.clear();
        this.cachedEmptyBlockSections.clear();
        this.dirtyBlockSections.clear();
        this.blockSections.clear();
        this.entities.clear();
        this.cachedBlockSectionWorld = null;
        this.cachedBlockSectionRange = null;
        this.blockSectionCacheAge = 0;
    }

    //-----block section manipulation-----
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
        Set<PhysicsCollideWith> sectionCaches = CACHES_BY_SECTION.get(sectionKey);
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
                    boolean liquid = PhysXBlockSectionCollider.isLiquid(state);
                    if (material.equals(Material.AIR) || (!liquid && !material.blocksMovement())) continue;
                    builder.addBlock(mutablePos, state, liquid);
                }
            }
        }

        return builder.isEmpty() ? null : builder.build();
    }

    private boolean areChunksLoaded(World world, BlockSection.Range sectionRange) {
        ChunkProviderServer serverChunkProvider = (ChunkProviderServer) world.getChunkProvider();
        for (int chunkX = sectionRange.minX(); chunkX <= sectionRange.maxX(); chunkX++) {
            for (int chunkZ = sectionRange.minZ(); chunkZ <= sectionRange.maxZ(); chunkZ++) {
                if (!serverChunkProvider.chunkExists(chunkX, chunkZ)) return false;
            }
        }
        return true;
    }

    private BlockPos growMin(BlockPos pos, int grow) {
        return new BlockPos(
                pos.getX() - grow,
                Math.max(0, pos.getY() - grow),
                pos.getZ() - grow
        );
    }

    private BlockPos growMax(BlockPos pos, World world, int grow) {
        return new BlockPos(
                pos.getX() + grow,
                Math.min(world.getHeight() - 1, pos.getY() + grow),
                pos.getZ() + grow
        );
    }

    public List<Entity> getEntities() {
        return this.entities;
    }

    public List<BlockSection> getBlockSections() {
        return this.blockSections;
    }

    private boolean isEntityCollidable(Entity entity, World hostWorld) {
        return entity != null
                && entity.isEntityAlive()
                && !entity.noClip
                && entity.world == hostWorld
                && !(entity instanceof EntityPlayer)
                && !(entity instanceof EntityItem)
                && !(entity instanceof EntityFireball)
                && !(entity instanceof EntityMountable)
                && !(entity.getRidingEntity() instanceof EntityMountable);
    }

    //-----stuff for editing caches by section map-----
    public static void invalidateBlockSectionAt(@NotNull World world, @NotNull BlockPos pos) {
        BlockSection.Key sectionKey = BlockSection.Key.fromBlockPos(world, pos);
        Set<PhysicsCollideWith> sectionCaches = CACHES_BY_SECTION.get(sectionKey);
        if (sectionCaches == null) return;

        for (PhysicsCollideWith collideWith : new ArrayList<>(sectionCaches)) {
            if (collideWith.registeredBlockSections.contains(sectionKey)) collideWith.dirtyBlockSections.add(sectionKey);
        }
    }

    public static void clearBlockSectionRegistrationsForWorld(@NotNull World world) {
        for (Map.Entry<BlockSection.Key, Set<PhysicsCollideWith>> entry : CACHES_BY_SECTION.entrySet()) {
            if (entry.getKey().world() != world) continue;
            for (PhysicsCollideWith collideWith : new ArrayList<>(entry.getValue())) {
                collideWith.close();
            }
        }
    }
}
