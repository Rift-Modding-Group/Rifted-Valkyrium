package org.valkyrienskies.mod.common.ships.block_relocation;

import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.management.PlayerList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import org.jetbrains.annotations.NotNull;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import valkyrienwarfare.api.event.ShipBlockRelocationEvent;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public class BlockRelocation {
    /**
     * Captures external block data and returns the actions to run after the destination is ready.
     * */
    @NotNull
    public static Runnable prepare(
            @NotNull World world, @NotNull Iterable<BlockPos> sourceBlocks,
            @NotNull BlockPos offset, boolean assembling
    ) {
        List<Runnable> restorationActions = new ArrayList<>();
        MinecraftForge.EVENT_BUS.post(new ShipBlockRelocationEvent.PreCopied(world, sourceBlocks, offset, assembling, restorationActions::add));
        return () -> {
            List<Runnable> pending = List.copyOf(restorationActions);
            restorationActions.clear();
            RuntimeException failure = null;
            for (Runnable action : pending) {
                try {
                    action.run();
                }
                catch (RuntimeException exception) {
                    if (failure == null) failure = exception;
                    else if (failure != exception) failure.addSuppressed(exception);
                }
            }
            if (failure != null) throw failure;
        };
    }

    public static void relocatePlayerBeds(@NotNull WorldServer world, @NotNull Predicate<BlockPos> containsSourceBlock, @NotNull BlockPos offset) {
        int dimension = world.provider.getDimension();
        if (world.getMinecraftServer() == null) return;

        //for online players
        PlayerList playerList = world.getMinecraftServer().getPlayerList();
        Set<String> onlinePlayerFiles = new HashSet<>();
        for (EntityPlayerMP player : playerList.getPlayers()) {
            onlinePlayerFiles.add(player.getCachedUniqueIdString() + ".dat");
            BlockPos bed = player.getBedLocation(dimension);
            if (bed != null && containsSourceBlock.test(bed)) {
                player.setSpawnChunk(bed.add(offset), player.isSpawnForced(dimension), dimension);
            }
            if (player.dimension == dimension && player.bedLocation != null && containsSourceBlock.test(player.bedLocation)) {
                player.bedLocation = player.bedLocation.add(offset);
            }
        }

        WorldServer overworld = world.getMinecraftServer().worlds[0];
        NBTTagCompound ownerData = overworld.getWorldInfo().getPlayerNBTTagCompound();
        if (ownerData != null) relocateSavedPlayerBeds(ownerData, dimension, containsSourceBlock, offset);

        //include offline players
        Path playerDirectory = overworld.getSaveHandler().getWorldDirectory().toPath().resolve("playerdata");
        if (!Files.isDirectory(playerDirectory)) return;
        try (DirectoryStream<Path> playerFiles = Files.newDirectoryStream(playerDirectory, "*.dat")) {
            for (Path playerFile : playerFiles) {
                if (onlinePlayerFiles.contains(playerFile.getFileName().toString())) continue;
                try {
                    NBTTagCompound playerData;
                    try (InputStream input = Files.newInputStream(playerFile)) {
                        playerData = CompressedStreamTools.readCompressed(input);
                    }
                    if (!relocateSavedPlayerBeds(playerData, dimension, containsSourceBlock, offset)) continue;
                    Path temporaryFile = playerFile.resolveSibling(playerFile.getFileName() + ".tmp");
                    try (OutputStream output = Files.newOutputStream(temporaryFile)) {
                        CompressedStreamTools.writeCompressed(playerData, output);
                    }
                    Files.move(temporaryFile, playerFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                }
                catch (IOException exception) {
                    ValkyrienSkiesMod.LOGGER.error("Failed to relocate bed spawn in {}", playerFile, exception);
                }
            }
        }
        catch (IOException exception) {
            ValkyrienSkiesMod.LOGGER.error("Failed to read player bed spawns from {}", playerDirectory, exception);
        }
    }

    public static boolean relocateSavedPlayerBeds(
            @NotNull NBTTagCompound playerData, int dimension,
            @NotNull Predicate<BlockPos> containsSourceBlock, @NotNull BlockPos offset
    ) {
        List<NBTTagCompound> spawns = new ArrayList<>();
        if (dimension == 0) spawns.add(playerData);
        NBTTagList dimensionSpawns = playerData.getTagList("Spawns", 10);
        for (int index = 0; index < dimensionSpawns.tagCount(); index++) {
            NBTTagCompound spawn = dimensionSpawns.getCompoundTagAt(index);
            if (spawn.getInteger("Dim") == dimension) spawns.add(spawn);
        }
        boolean changed = false;
        for (NBTTagCompound spawn : spawns) {
            if (!spawn.hasKey("SpawnX", 99) || !spawn.hasKey("SpawnY", 99) || !spawn.hasKey("SpawnZ", 99)) continue;
            BlockPos bed = new BlockPos(spawn.getInteger("SpawnX"), spawn.getInteger("SpawnY"), spawn.getInteger("SpawnZ"));
            if (!containsSourceBlock.test(bed)) continue;
            BlockPos destination = bed.add(offset);
            spawn.setInteger("SpawnX", destination.getX());
            spawn.setInteger("SpawnY", destination.getY());
            spawn.setInteger("SpawnZ", destination.getZ());
            changed = true;
        }
        return changed;
    }
}
