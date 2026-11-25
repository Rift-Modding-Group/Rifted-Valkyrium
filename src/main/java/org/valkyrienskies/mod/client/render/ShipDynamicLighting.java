package org.valkyrienskies.mod.client.render;

import net.minecraft.block.state.IBlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.ships.chunk_claims.ShipChunkAllocator;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.WorldClientShipManager;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Helps out in lights coming from ships spreading to the world, clientside
 */
public class ShipDynamicLighting {
    public static int MAX_SHADER_LIGHTS = 32;
    private final Map<Chunk, List<Light>> sources = new HashMap<>();
    private final Set<Long> dirtyChunks = new HashSet<>();
    public volatile List<Light> lights = List.of();

    @NotNull
    public static List<Light> collect(
            @NotNull ShipDynamicLighting lighting, @NotNull World world,
            @NotNull Iterable<Chunk> chunks, @NotNull ShipTransform transform
    ) {
        List<Light> lights = new ArrayList<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (Chunk chunk : chunks) {
            if (chunk == null || chunk.isEmpty()) continue;
            List<Light> local = lighting.sources.get(chunk);
            boolean dirty = lighting.dirtyChunks.remove(ChunkPos.asLong(chunk.x, chunk.z));
            if (local == null || dirty) {
                local = new ArrayList<>();
                for (ExtendedBlockStorage section : chunk.getBlockStorageArray()) {
                    if (section == null || section.isEmpty()) continue;
                    for (int y = 0; y < 16; y++) {
                        for (int z = 0; z < 16; z++) {
                            for (int x = 0; x < 16; x++) {
                                IBlockState state = section.get(x, y, z);
                                pos.setPos((chunk.x << 4) + x, section.getYLocation() + y, (chunk.z << 4) + z);
                                int level = Math.clamp(state.getLightValue(world, pos), 0, 15);
                                if (level > 0) local.add(new Light(pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D, level));
                            }
                        }
                    }
                }
                lighting.sources.put(chunk, List.copyOf(local));
            }
            for (Light light : local) {
                Vector3d global = transform.getSubspaceToGlobal().transformPosition(new Vector3d(light.x(), light.y(), light.z()));
                lights.add(new Light(global.x, global.y, global.z, light.level()));
            }
        }
        return lights;
    }

    public static void invalidate(@NotNull World world, @NotNull BlockPos pos) {
        if (world.isRemote && ShipChunkAllocator.isBlockInShipyard(pos)
                && ValkyrienUtils.getPhysObjWorld(world) instanceof WorldClientShipManager manager) {
            manager.dynamicLighting.dirtyChunks.add(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        }
    }

    public static void update(@NotNull ShipDynamicLighting lighting, @NotNull World world, @NotNull List<Light> lights) {
        if (world.isRemote) lighting.lights = List.copyOf(lights);
    }

    public static void retainChunks(@NotNull ShipDynamicLighting lighting, @NotNull Set<Chunk> chunks) {
        lighting.sources.keySet().retainAll(chunks);
        lighting.dirtyChunks.clear();
    }

    public static int getCombinedLight(@NotNull World world, @NotNull BlockPos pos, int packedLight) {
        if (!world.isRemote || ShipChunkAllocator.isBlockInShipyard(pos)) return packedLight;
        if (ValkyrienUtils.getPhysObjWorld(world) instanceof WorldClientShipManager manager) {
            return getCombinedLight(manager.dynamicLighting, pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D, packedLight);
        }
        return packedLight;
    }

    public static int getCombinedLight(@NotNull ShipDynamicLighting lighting, double x, double y, double z, int packedLight) {
        int block = packedLight & 65535;
        for (Light light : lighting.lights) {
            double dx = x - light.x();
            double dy = y - light.y();
            double dz = z - light.z();
            double distanceSquared = dx * dx + dy * dy + dz * dz;
            if (distanceSquared >= light.level() * light.level()) continue;
            block = Math.max(block, (int) ((light.level() - Math.sqrt(distanceSquared)) * 16D));
        }
        return packedLight & 0xFFFF0000 | block;
    }

    public record Light(double x, double y, double z, int level) {}
}
