package org.valkyrienskies.mod.common.physics;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderServer;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.MutablePair;
import org.valkyrienskies.mod.common.entity.EntityMountable;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.ArrayList;
import java.util.List;

/**
 * This class stores the world chunks and entities that a ship is going to collide with.
 * */
public class PhysicsCollideWith {
    public static final int MAX_ENTITIES = 512;
    public static final double ENTITY_SCAN_GROW = 2D;

    private ChunkCache cachedChunks;
    private final MutablePair<BlockPos, BlockPos> cachedChunkCorners = new MutablePair<>(BlockPos.ORIGIN, BlockPos.ORIGIN);
    private final List<Entity> entities = new ArrayList<>();

    public void onUpdate(PhysicsObject physicsObject) {
        ChunkCache nextCachedChunks = null;
        BlockPos nextCachedMin = null;
        BlockPos nextCachedMax = null;
        List<Entity> nextEntities = new ArrayList<>();

        AxisAlignedBB shipAabb = physicsObject.getPhysicsTransformAABB();
        if (shipAabb != null) {
            World world = physicsObject.getWorld();
            nextCachedMin = new BlockPos(
                    (int) Math.floor(shipAabb.minX),
                    Math.max(0, (int) Math.floor(shipAabb.minY)),
                    (int) Math.floor(shipAabb.minZ)
            );
            nextCachedMax = new BlockPos(
                    (int) Math.ceil(shipAabb.maxX),
                    Math.min(world.getHeight() - 1, (int) Math.ceil(shipAabb.maxY)),
                    (int) Math.ceil(shipAabb.maxZ)
            );

            //---chunk scanning---
            boolean areSurroundingChunksLoaded = true;
            ChunkProviderServer serverChunkProvider = (ChunkProviderServer) world.getChunkProvider();
            int chunkMinX = nextCachedMin.getX() >> 4;
            int chunkMaxX = nextCachedMax.getX() >> 4;
            int chunkMinZ = nextCachedMin.getZ() >> 4;
            int chunkMaxZ = nextCachedMax.getZ() >> 4;

            outer: for (int chunkX = chunkMinX; chunkX <= chunkMaxX; chunkX++) {
                for (int chunkZ = chunkMinZ; chunkZ <= chunkMaxZ; chunkZ++) {
                    areSurroundingChunksLoaded = serverChunkProvider.chunkExists(chunkX, chunkZ);
                    if (!areSurroundingChunksLoaded) break outer;
                }
            }

            if (areSurroundingChunksLoaded) {
                synchronized (this) {
                    if (this.cachedChunks != null && nextCachedMin.equals(this.cachedChunkCorners.getLeft()) && nextCachedMax.equals(this.cachedChunkCorners.getRight())) {
                        nextCachedChunks = this.cachedChunks;
                    }
                }
                if (nextCachedChunks == null) {
                    nextCachedChunks = new ChunkCache(world, nextCachedMin, nextCachedMax, 0);
                }
            }
            else {
                nextCachedMin = null;
                nextCachedMax = null;
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
            this.cachedChunks = nextCachedChunks;
            this.cachedChunkCorners.setLeft(nextCachedMin);
            this.cachedChunkCorners.setRight(nextCachedMax);
            this.entities.clear();
            this.entities.addAll(nextEntities);
        }
    }

    public synchronized ChunkCache getCachedChunks() {
        return this.cachedChunks;
    }

    public synchronized MutablePair<BlockPos, BlockPos> getCachedChunkCorners() {
        return this.cachedChunkCorners;
    }

    public List<Entity> getEntities() {
        return this.entities;
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
}
