package org.valkyrienskies.mod.common;

import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityFallingBlock;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.MovementInput;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.Explosion;
import net.minecraft.world.World;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.event.world.ExplosionEvent;
import net.minecraftforge.event.world.WorldEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.eventhandler.Event.Result;
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
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.ship_pilot.IShipPilot;
import org.valkyrienskies.mod.common.capability.ship_world.IShipWorld;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_world.*;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import java.util.List;
import java.util.Optional;

@EventBusSubscriber(modid = ValkyrienSkiesMod.MOD_ID)
public class EventsCommon {
    private static final int PLAYER_WORLD_LIMIT = 27_000_000;

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
            spawningShip.getShipTransformationManager()
                    .getCurrentTickTransform()
                    .transform(entity, TransformType.SUBSPACE_TO_GLOBAL, false);

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
    public static void onPlayerInteractEvent(PlayerInteractEvent event) {
        BlockPos pos = event.getPos();

        Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(event.getWorld(), pos);
        if (physicsObject.isPresent()) event.setResult(Result.ALLOW);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onBlockBreakFirst(BlockEvent event) {
        ValkyrienUtils.getPhysoManagingBlock(event.getWorld(), event.getPos())
            .ifPresent(physicsObject -> event.setResult(Result.ALLOW));
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

    /**
     * A replacement for MovementInputFromOptions.class
     *
     * This blocks player movement when they're piloting from a control tileentity, like
     * the captains chair
     * */
    @SubscribeEvent
    public static void onInputUpdate(InputUpdateEvent event) {
        IShipPilot pilot = event.getEntityPlayer().getCapability(VSCapabilityRegistry.VS_SHIP_PILOT, null);
        if (pilot == null) return;

        if (pilot.isPilotingShip()) {
            MovementInput input = event.getMovementInput();
            input.moveStrafe = 0.0F;
            input.moveForward = 0.0F;
            input.jump = false;
            input.sneak = false;
        }
    }
}
