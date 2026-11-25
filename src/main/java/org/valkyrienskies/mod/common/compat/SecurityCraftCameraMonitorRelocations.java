package org.valkyrienskies.mod.common.compat;

import net.geforcemods.securitycraft.items.CameraMonitorItem;
import net.geforcemods.securitycraft.misc.GlobalPos;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.MapStorage;
import net.minecraft.world.storage.WorldSavedData;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SecurityCraftCameraMonitorRelocations extends WorldSavedData {
    private final List<Relocation> relocations = new ArrayList<>();

    public SecurityCraftCameraMonitorRelocations(String name) {
        super(name);
    }

    @Nullable
    public static SecurityCraftCameraMonitorRelocations get(@NotNull World world) {
        MinecraftServer server = world.getMinecraftServer();
        if (server == null) return null;

        WorldServer overworld = server.getWorld(0);
        if (overworld == null) return null;

        MapStorage storage = overworld.getMapStorage();
        if (storage == null) return null;

        String name = "valkyrienskies_security_cameras";
        SecurityCraftCameraMonitorRelocations data = (SecurityCraftCameraMonitorRelocations) storage.getOrLoadData(SecurityCraftCameraMonitorRelocations.class, name);
        if (data == null) {
            data = new SecurityCraftCameraMonitorRelocations(name);
            storage.setData(name, data);
        }
        return data;
    }

    public void record(int dimension, @NotNull Map<BlockPos, BlockPos> cameras) {
        this.relocations.add(new Relocation(dimension, Map.copyOf(cameras)));
        this.markDirty();
    }

    public boolean updateMonitor(@NotNull ItemStack stack) {
        if (!(stack.getItem() instanceof CameraMonitorItem)) return false;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return false;
        String revisionKey = "RiftedValkyriumCameraRelocation";
        int revision = Math.clamp(tag.getInteger(revisionKey), 0, this.relocations.size());
        if (revision == this.relocations.size()) return false;

        List<Pair<GlobalPos, String>> cameras = CameraMonitorItem.getCameraPositions(tag);
        for (int slot = 0; slot < cameras.size(); slot++) {
            GlobalPos position = cameras.get(slot).getLeft();
            if (position == null) continue;
            GlobalPos original = position;

            for (int index = revision; index < this.relocations.size(); index++) {
                Relocation relocation = this.relocations.get(index);
                if (relocation.dimension() != position.dimension()) continue;
                BlockPos destination = relocation.cameras().get(position.pos());
                if (destination != null) position = GlobalPos.of(position.dimension(), destination);
            }
            if (!position.equals(original)) tag.setString("Camera" + (slot + 1), position.toNBTString());
        }
        tag.setInteger(revisionKey, this.relocations.size());
        return true;
    }

    @Override
    public void readFromNBT(@NotNull NBTTagCompound nbt) {
        this.relocations.clear();
        NBTTagList entries = nbt.getTagList("Relocations", 10);
        for (int index = 0; index < entries.tagCount(); index++) {
            NBTTagCompound entry = entries.getCompoundTagAt(index);
            NBTTagList moves = entry.getTagList("Cameras", 10);
            Map<BlockPos, BlockPos> cameras = new HashMap<>();
            for (int move = 0; move < moves.tagCount(); move++) {
                NBTTagCompound camera = moves.getCompoundTagAt(move);
                cameras.put(BlockPos.fromLong(camera.getLong("From")), BlockPos.fromLong(camera.getLong("To")));
            }
            this.record(entry.getInteger("Dimension"), cameras);
        }
        this.setDirty(false);
    }

    @NotNull
    @Override
    public NBTTagCompound writeToNBT(@NotNull NBTTagCompound nbt) {
        NBTTagList entries = new NBTTagList();
        for (Relocation relocation : this.relocations) {
            NBTTagCompound entry = new NBTTagCompound();
            entry.setInteger("Dimension", relocation.dimension());
            NBTTagList moves = new NBTTagList();
            relocation.cameras().forEach((source, destination) -> {
                NBTTagCompound camera = new NBTTagCompound();
                camera.setLong("From", source.toLong());
                camera.setLong("To", destination.toLong());
                moves.appendTag(camera);
            });
            entry.setTag("Cameras", moves);
            entries.appendTag(entry);
        }
        nbt.setTag("Relocations", entries);
        return nbt;
    }

    private record Relocation(int dimension, Map<BlockPos, BlockPos> cameras) {}
}
