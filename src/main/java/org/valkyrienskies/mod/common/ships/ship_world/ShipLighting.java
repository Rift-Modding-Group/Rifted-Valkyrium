package org.valkyrienskies.mod.common.ships.ship_world;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.NotNull;

import java.util.BitSet;
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

    /**
     * Preserve the implicit light values of an empty section without relighting the whole chunk.
     * */
    public static void initializeLightStorage(@NotNull Chunk chunk, int section, boolean hasSkyLight) {
        ExtendedBlockStorage[] storage = chunk.getBlockStorageArray();
        if (section < 0 || section >= storage.length || storage[section] != null) return;

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
        storage[section] = created;
    }
}
