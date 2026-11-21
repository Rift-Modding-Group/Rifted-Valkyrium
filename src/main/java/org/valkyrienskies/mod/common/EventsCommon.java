package org.valkyrienskies.mod.common;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerSetSpawnEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import net.minecraftforge.fml.common.gameevent.TickEvent.PlayerTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent;
import net.minecraftforge.fml.relauncher.Side;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.anchored_mount.ImplCapabilityShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.ship_world.IShipWorld;
import org.valkyrienskies.mod.common.item.ItemEntityShipLinkerStick;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_world.*;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;
import valkyrienwarfare.api.IShipData;
import valkyrienwarfare.api.RiftedValkyriumAPI;
import valkyrienwarfare.api.ShipEntityDeathBehavior;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@EventBusSubscriber(modid = ValkyrienSkiesMod.MOD_ID)
public class EventsCommon {
    private static final int PLAYER_WORLD_LIMIT = 27_000_000;

    @SubscribeEvent
    public static void onPlayerSetSpawn(@NotNull PlayerSetSpawnEvent event) {
        if (event.isForced() || event.getNewSpawn() == null) return;

        ShipData ship = ValkyrienUtils.getShipManagingBlock(event.getEntityPlayer().world, event.getNewSpawn()).orElse(null);
        if (ship != null && ship.dontSetSpawn) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(@NotNull PlayerInteractEvent.EntityInteract event) {
        handleEntityShipLink(event, event.getTarget());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteractSpecific(@NotNull PlayerInteractEvent.EntityInteractSpecific event) {
        handleEntityShipLink(event, event.getTarget());
    }

    private static void handleEntityShipLink(@NotNull PlayerInteractEvent event, @NotNull Entity target) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof ItemEntityShipLinkerStick)) return;
        event.setCanceled(true);
        event.setCancellationResult(EnumActionResult.SUCCESS);
        if (!(event.getWorld() instanceof WorldServer world)) return;

        EntityPlayer player = event.getEntityPlayer();
        NBTTagCompound tag = stack.getTagCompound();
        UUID selectedShipId = tag == null ? null : tag.getUniqueId(ItemEntityShipLinkerStick.SELECTED_SHIP);
        if (tag == null || !tag.hasUniqueId(ItemEntityShipLinkerStick.SELECTED_SHIP) || selectedShipId == null
                || tag.getInteger(ItemEntityShipLinkerStick.SELECTED_DIMENSION) != world.provider.getDimension()
        ) {
            player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.linker.select_ship"), true);
            return;
        }

        IShipData ship = RiftedValkyriumAPI.getShipByUUID(world, selectedShipId).orElse(null);
        if (ship == null) {
            player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.linker.ship_missing"), true);
            return;
        }

        if (target.isDead) return;

        AxisAlignedBB bounds = target.getEntityBoundingBox();
        RiftedValkyriumAPI.linkShipToEntity(
                world, ship, target, new Vec3d(0D, bounds.maxY - bounds.minY, 0D),
                ShipEntityDeathBehavior.DELETE_SHIP
        );
        tag.removeTag(ItemEntityShipLinkerStick.SELECTED_SHIP + "Most");
        tag.removeTag(ItemEntityShipLinkerStick.SELECTED_SHIP + "Least");
        tag.removeTag(ItemEntityShipLinkerStick.SELECTED_DIMENSION);
        player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.linker.linked", ship.getName(), target.getDisplayName()), true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityJoinWorldEvent(EntityJoinWorldEvent event) {
        Entity entity = event.getEntity();

        World world = entity.world;
        BlockPos posAt = new BlockPos(entity);

        IShipAnchoredMount mount = entity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        if (mount != null) mount.tryAnchorMount(entity);

        Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(world, posAt);
        //peg anything thats not a falling block to ship movement
        if (!event.getWorld().isRemote && physicsObject.isPresent() && !(entity instanceof EntityFallingBlock)) {
            PhysicsObject spawningShip = physicsObject.get();

            //turn entity position in world space to ship space
            if (mount != null && mount.isAnchoredToShip() && ImplCapabilityShipAnchoredMount.isAlignedEntity(entity.getClass())) {
                Vec3d position = spawningShip.transformVector(mount.getLocalMountPos(), TransformType.SUBSPACE_TO_GLOBAL);
                entity.setPosition(position.x, position.y, position.z);
            }
            else {
                spawningShip.getShipTransformationManager()
                        .getCurrentTickTransform()
                        .transform(entity, TransformType.SUBSPACE_TO_GLOBAL, false);
            }

            //now peg the entity to the ship to ensure it moves alongside it
            EntityDraggable.startTrackingShip(entity, spawningShip.getShipData());
        }
        //make sure that thrown items get pegged to the ship
        else if (!event.getWorld().isRemote && entity instanceof EntityItem entityItem) {
            String throwerName = entityItem.getThrower();
            if (throwerName == null) return;

            EntityPlayer thrower = entityItem.world.getPlayerEntityByName(throwerName);
            if (thrower != null) EntityDraggable.copyShipTracking(thrower, entityItem);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDeath(@NotNull LivingDeathEvent event) {
        if (event.getEntityLiving().world.isRemote) return;
        IPhysObjectWorld shipWorld = ValkyrienUtils.getPhysObjWorld(event.getEntityLiving().world);
        if (shipWorld instanceof WorldServerShipManager manager) {
            manager.onCarrierDeath(event.getEntityLiving());
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingDrops(@NotNull LivingDropsEvent event) {
        if (event.getEntityLiving().world.isRemote) return;

        for (EntityItem item : event.getDrops()) {
            EntityDraggable.copyShipTracking(event.getEntityLiving(), item);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWorldTickEvent(WorldTickEvent event) {
        // This only gets called server side, because forge wants it that way. But in case they
        // change their mind, this exception will crash the game to notify us of the change.
        if (event.side == Side.CLIENT) {
            throw new IllegalStateException("This event should never get called client side");
        }
        World world = event.world;
        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        if (physObjectWorld == null) {
            throw new IllegalStateException("Could not get ship manager from world!");
        }
        switch (event.phase) {
            case START:
                // Reset the air pocket status of all entities
                for (final Entity entity : world.loadedEntityList) {
                    IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
                    if (draggable == null) continue;
                    draggable.decrementTicksAirPocket();
                }
                break;
            case END:
                physObjectWorld.tick();
                EntityDraggable.tickAddedVelocityForWorld(world);
                IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
                if (shipWorld != null) {
                    shipWorld.getLighting().process(pos -> world.checkLightFor(EnumSkyBlock.SKY, pos), System::nanoTime);
                }
                break;
        }
    }

    //prevent players from somehow going to the shipyard
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onPlayerTickEvent(@NotNull PlayerTickEvent event) {
        if (event.player.world.isRemote || event.phase != Phase.END) return;

        EntityPlayerMP player = (EntityPlayerMP) event.player;
        if (!isOutsidePlayableWorld(player.posX, player.posZ)) return;

        double returnX = player.lastTickPosX;
        double returnY = player.lastTickPosY;
        double returnZ = player.lastTickPosZ;
        if (isOutsidePlayableWorld(returnX, returnZ)) {
            BlockPos spawnPosition = player.getServerWorld().getSpawnPoint();
            returnX = spawnPosition.getX() + 0.5D;
            returnY = spawnPosition.getY();
            returnZ = spawnPosition.getZ() + 0.5D;
        }

        player.attemptTeleport(returnX, returnY, returnZ);
        player.sendMessage(new TextComponentTranslation(
                "message.valkyrienskies.shipyard_boundary", PLAYER_WORLD_LIMIT
        ));
    }

    private static boolean isOutsidePlayableWorld(double x, double z) {
        return Math.abs(x) > PLAYER_WORLD_LIMIT || Math.abs(z) > PLAYER_WORLD_LIMIT;
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWorldLoad(WorldEvent.Load event) {
        World world = event.getWorld();
        IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld == null) return;

        event.getWorld().addEventListener(new VSWorldEventListener(world));
        if (!event.getWorld().isRemote) shipWorld.setManager(new WorldServerShipManager(world));
        else shipWorld.setManager(new WorldClientShipManager(world));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onWorldUnload(WorldEvent.Unload event) {
        World world = event.getWorld();
        IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld == null) return;

        shipWorld.getManager().onWorldUnload();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChunkLoad(ChunkEvent.Load event) {
        invalidateBlockSectionCacheForChunk(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onChunkUnload(ChunkEvent.Unload event) {
        invalidateBlockSectionCacheForChunk(event);
    }

    private static void invalidateBlockSectionCacheForChunk(ChunkEvent event) {
        World world = event.getWorld();
        if (world.isRemote) return;

        IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld != null && shipWorld.getManager() != null) {
            shipWorld.getManager().invalidateBlockSectionsInChunk(event.getChunk().x, event.getChunk().z);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onExplosionStart(ExplosionEvent.Start event) {
        // Only run on server side
        if (event.getWorld().isRemote) return;

        IShipWorld shipWorld = event.getWorld().getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld == null) return;

        Explosion explosion = event.getExplosion();
        Vector3dc center = new Vector3d(
                explosion.getPosition().x,
                explosion.getPosition().y,
                explosion.getPosition().z
        );
        Optional<PhysicsObject> optionalPhysicsObject = ValkyrienUtils.getPhysoManagingBlock(event.getWorld(),
                new BlockPos(event.getExplosion().getPosition()));
        if (optionalPhysicsObject.isPresent()) {
            return;
        }
        // Explosion radius
        float radius = explosion.size;
        AxisAlignedBB toCheck = new AxisAlignedBB(
                center.x() - radius, center.y() - radius,center.z() - radius,
                center.x() + radius, center.y() + radius, center.z() + radius
        );

        // Find nearby ships, we will check if the explosion effects them
        List<PhysicsObject> shipsNear = shipWorld.getManager().getPhysObjectsInAABB(toCheck);

        // Process the explosion on the nearby ships
        for (PhysicsObject ship : shipsNear) {
            Vector3d inLocal = new Vector3d(center);
            inLocal.mulPosition(ship.getShipTransform().getGlobalToSubspace());

            Explosion expl = new Explosion(
                    event.getWorld(), explosion.exploder, inLocal.x, inLocal.y,
                    inLocal.z, radius, explosion.causesFire, explosion.damagesTerrain
            );

            double waterRange = .6D;

            for (int x = (int) Math.floor(inLocal.x - waterRange); x <= Math.ceil(inLocal.x + waterRange); x++) {
                for (int y = (int) Math.floor(inLocal.y - waterRange); y <= Math.ceil(inLocal.y + waterRange); y++) {
                    for (int z = (int) Math.floor(inLocal.z - waterRange); z <= Math.ceil(inLocal.z + waterRange); z++) {
                        IBlockState state = event.getWorld().getBlockState(new BlockPos(x, y, z));
                        if (state.getBlock() instanceof BlockLiquid) return;
                    }
                }
            }

            expl.doExplosionA();
            event.getExplosion().getAffectedBlockPositions().addAll(expl.getAffectedBlockPositions());
        }
    }

}
