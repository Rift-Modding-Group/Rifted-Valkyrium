package org.valkyrienskies.mod.common.ships.entity_interaction;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import valkyrienwarfare.api.TransformType;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ParametersAreNonnullByDefault
public class ShipEntityUnloading {
    private static final Set<Class<? extends Entity>> registeredClasses = ConcurrentHashMap.newKeySet();

    public static void register(Class<? extends Entity> entityClass) {
        registeredClasses.add(Objects.requireNonNull(entityClass, "entityClass"));
    }

    public static boolean isRegistered(Class<? extends Entity> entityClass) {
        if (EntityPlayer.class.isAssignableFrom(entityClass)) return false;
        return registeredClasses.stream().anyMatch(registered -> registered.isAssignableFrom(entityClass));
    }

    public static void unload(World world, ShipData data) {
        for (Entity root : new ArrayList<>(world.loadedEntityList)) {
            if (root.isDead || root.isRiding() || !isRegistered(root.getClass())) continue;

            ShipData activeShip = EntityDraggable.getActiveShip(root);
            if (activeShip == null || !data.getUuid().equals(activeShip.getUuid())) continue;

            List<Entity> group = new ArrayList<>();
            group.add(root);
            group.addAll(root.getRecursivePassengers());
            if (group.stream().anyMatch(entity -> entity instanceof EntityPlayer || entity.isDead)) continue;
            if (group.stream().anyMatch(entity -> {
                Entity vehicle = entity.getRidingEntity();
                return vehicle != null && isRegistered(entity.getClass()) && !isRegistered(vehicle.getClass());
            })) {
                continue;
            }

            NBTTagList entities = new NBTTagList();
            try {
                for (Entity entity : group) {
                    NBTTagCompound tag = new NBTTagCompound();
                    if (!entity.writeToNBTAtomically(tag)) break;

                    tag.removeTag("Passengers");
                    Vector3d position = new Vector3d(entity.posX, entity.posY, entity.posZ);
                    data.getShipTransform().transformPosition(position, TransformType.GLOBAL_TO_SUBSPACE);
                    NBTTagCompound localPosition = tag.getCompoundTag("VSShipPosition");
                    localPosition.setUniqueId("Ship", data.getUuid());
                    if (!localPosition.hasKey("Anchor", 4)) {
                        localPosition.setDouble("X", position.x);
                        localPosition.setDouble("Y", position.y);
                        localPosition.setDouble("Z", position.z);
                    }
                    tag.setTag("VSShipPosition", localPosition);
                    Entity vehicle = entity.getRidingEntity();
                    if (vehicle != null) {
                        tag.setUniqueId("RVVehicle", vehicle.getUniqueID());
                    }
                    entities.appendTag(tag);
                }
                if (entities.tagCount() != group.size()) continue;

                NBTTagCompound savedGroup = new NBTTagCompound();
                savedGroup.setTag("Entities", entities);
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                CompressedStreamTools.writeCompressed(savedGroup, bytes);
                data.unloadedEntities.add(bytes.toByteArray());
            }
            catch (IOException | RuntimeException exception) {
                ValkyrienSkiesMod.LOGGER.error("Could not save entities aboard ship {}", data.getUuid(), exception);
                continue;
            }
            removeEntities(world, group);
        }
    }

    public static void reload(PhysicsObject ship) {
        if (!(ship.getWorld() instanceof WorldServer world)) return;
        ShipData data = ship.getShipData();
        Iterator<byte[]> iterator = data.unloadedEntities.iterator();
        while (iterator.hasNext()) {
            List<Entity> spawned = new ArrayList<>();
            try {
                NBTTagCompound savedGroup = CompressedStreamTools.readCompressed(new ByteArrayInputStream(iterator.next()));
                NBTTagList tags = savedGroup.getTagList("Entities", 10);
                Map<UUID, Entity> entities = new LinkedHashMap<>();
                for (int index = 0; index < tags.tagCount(); index++) {
                    NBTTagCompound tag = tags.getCompoundTagAt(index);
                    Entity entity = EntityList.createEntityFromNBT(tag, world);
                    if (entity == null || entity instanceof EntityPlayer
                            || world.getEntityFromUuid(entity.getUniqueID()) != null) break;

                    NBTTagCompound localPosition = tag.getCompoundTag("VSShipPosition");
                    Vector3d position = new Vector3d(localPosition.getDouble("X"), localPosition.getDouble("Y"), localPosition.getDouble("Z"));
                    data.getShipTransform().transformPosition(position, TransformType.SUBSPACE_TO_GLOBAL);
                    entity.setPosition(position.x, position.y, position.z);
                    entity.prevPosX = entity.lastTickPosX = position.x;
                    entity.prevPosY = entity.lastTickPosY = position.y;
                    entity.prevPosZ = entity.lastTickPosZ = position.z;
                    IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
                    if (draggable != null) draggable.clearPendingShipPosition();
                    EntityDraggable.startTrackingShip(entity, data);
                    entities.put(entity.getUniqueID(), entity);
                }
                if (entities.size() != tags.tagCount()) continue;

                for (int index = 0; index < tags.tagCount(); index++) {
                    NBTTagCompound tag = tags.getCompoundTagAt(index);
                    if (tag.hasUniqueId("RVVehicle")) {
                        Entity vehicle = entities.get(tag.getUniqueId("RVVehicle"));
                        entities.get(tag.getUniqueId("UUID")).startRiding(vehicle, true);
                    }
                }
                for (Entity entity : entities.values()) {
                    world.getChunk(MathHelper.floor(entity.posX) >> 4, MathHelper.floor(entity.posZ) >> 4);
                    if (!world.spawnEntity(entity)) break;
                    spawned.add(entity);
                }
                if (spawned.size() == entities.size()) {
                    iterator.remove();
                    continue;
                }
            }
            catch (IOException | RuntimeException exception) {
                ValkyrienSkiesMod.LOGGER.error("Could not restore entities aboard ship {}", data.getUuid(), exception);
            }
            removeEntities(world, spawned);
        }
    }

    private static void removeEntities(World world, List<Entity> entities) {
        for (Entity entity : entities) {
            Chunk chunk = world.getChunkProvider().getLoadedChunk(entity.chunkCoordX, entity.chunkCoordZ);
            if (entity.addedToChunk && chunk != null) chunk.removeEntity(entity);
            world.loadedEntityList.remove(entity);
            world.onEntityRemoved(entity);
        }
    }
}
