package org.valkyrienskies.mod.common.util;

import net.minecraft.block.BlockHorizontal;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.VSWorldDataCapability;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.ship_world.IShipWorld;
import org.valkyrienskies.mod.common.entity.EntityMountable;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.block_relocation.BlockFinder;
import org.valkyrienskies.mod.common.ships.chunk_claims.ShipChunkAllocator;
import org.valkyrienskies.mod.common.ships.chunk_claims.VSChunkClaim;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityShipMountData;
import org.valkyrienskies.mod.common.ships.ship_transform.CoordinateSpaceType;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.WorldServerShipManager;
import org.valkyrienskies.mod.common.util.names.NounListNameGenerator;
import org.valkyrienskies.mod.common.config.VSConfig;
import valkyrienwarfare.api.TransformType;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

/**
 * This class contains various helper functions for Valkyrien Skies.
 */
@ParametersAreNonnullByDefault
public final class ValkyrienUtils {
    private ValkyrienUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * The liver of this mod. Returns the PhysicsObject that managed the given pos in the given
     * world.
     *
     * @param world The World we are in
     * @param pos   A BlockPos within the physics object space.
     * @return The PhysicsObject that owns the chunk at pos within the given world.
     */
    @SuppressWarnings("ConstantConditions")
    public static @NotNull Optional<PhysicsObject> getPhysoManagingBlock(@Nullable World world, @Nullable BlockPos pos) {
        Optional<ShipData> shipData = getShipManagingBlock(world, pos);
        if (shipData.isEmpty()) return Optional.empty();

        IPhysObjectWorld physObjectWorld = getPhysObjWorld(world);
        if (physObjectWorld == null) throw new IllegalStateException("Could not get ship manager from world!");

        return shipData.map(data -> physObjectWorld.getPhysObjectFromUUID(data.getUuid()));
    }

    public static @NotNull Optional<ShipData> getShipManagingBlock(@Nullable World world, @Nullable BlockPos pos) {
        if (world == null ||
            pos == null ||
            !ShipChunkAllocator.isChunkInShipyard(pos.getX() >> 4, pos.getZ() >> 4)) {
            return Optional.empty();
        }

        return QueryableShipData.get(world)
            .getShipFromChunk(pos.getX() >> 4, pos.getZ() >> 4);
    }

    /**
     * If the given AxisAlignedBB is in ship space, then this will return that AxisAlignedBB
     * transformed to global space. Otherwise it just returns the input AxisAlignedBB.
     */
    public static @NotNull AxisAlignedBB getAABBInGlobal(AxisAlignedBB axisAlignedBB, @Nullable World world, @Nullable BlockPos pos) {
        Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(world, pos);
        if (physicsObject.isPresent()) {
            // We're in a physics object; convert the bounding box to a polygon; put its coordinates
            // in global space, and then return the bounding box that encloses all the points.
            TransformedAABB bbAsPoly = new TransformedAABB(axisAlignedBB, physicsObject.get()
                    .getShipTransformationManager()
                    .getCurrentTickTransform(), TransformType.SUBSPACE_TO_GLOBAL);
            return bbAsPoly.getEnclosedAABB();
        } else {
            return axisAlignedBB;
        }
    }

    @NotNull
    public static EntityShipMountData getMountedShipAndPos(Entity entity) {
        EntityShipMountData sleepingPlayerMount = getSleepingPlayerShipAndPos(entity);
        if (sleepingPlayerMount.isMounted()) {
            return sleepingPlayerMount;
        }

        Entity ridingEntity = entity.getRidingEntity();
        if (ridingEntity instanceof EntityMountable mountable) {
            Optional<PhysicsObject> mountedShip = mountable.getMountedShip();
            if (mountedShip.isPresent()) {
                return new EntityShipMountData(mountedShip.get(), mountable.getMountPos());
            }
        }

        return getAnchoredMountShipAndPos(entity);
    }

    /**
     * position of player on ship when sleepin on bed attached to it
     * */
    @NotNull
    public static EntityShipMountData getSleepingPlayerShipAndPos(Entity entity) {
        if (!(entity instanceof EntityPlayer player) || !player.isPlayerSleeping() || player.bedLocation == null) {
            return new EntityShipMountData();
        }

        Optional<PhysicsObject> mountedShip = getPhysoManagingBlock(player.world, player.bedLocation);
        if (mountedShip.isEmpty()) return new EntityShipMountData();

        IBlockState state = player.world.isBlockLoaded(player.bedLocation) ? player.world.getBlockState(player.bedLocation) : null;
        boolean isBed = state != null && state.getBlock().isBed(state, player.world, player.bedLocation, player);
        EnumFacing facing = isBed && state.getBlock() instanceof BlockHorizontal ? state.getValue(BlockHorizontal.FACING) : null;

        double offsetX = facing == null ? 0.5D : 0.5D + facing.getXOffset() * 0.4D;
        double offsetZ = facing == null ? 0.5D : 0.5D + facing.getZOffset() * 0.4D;
        Vec3d localSleepingPosition = new Vec3d(
                player.bedLocation.getX() + offsetX,
                player.bedLocation.getY() + 0.6875D,
                player.bedLocation.getZ() + offsetZ
        );
        return new EntityShipMountData(mountedShip.get(), localSleepingPosition);
    }

    /**
     * position of player on ship when sittin on chair attached to it
     * */
    @NotNull
    public static EntityShipMountData getAnchoredMountShipAndPos(Entity entity) {
        final Entity ridingEntity = entity.getRidingEntity();
        if (ridingEntity == null) return new EntityShipMountData();

        final IShipAnchoredMount anchoredMount = ridingEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        if (anchoredMount == null || (!anchoredMount.isAnchoredToShip() && !anchoredMount.tryAnchorMount(ridingEntity))) {
            return new EntityShipMountData();
        }

        final Optional<PhysicsObject> mountedShip = getPhysoManagingBlock(ridingEntity.world, anchoredMount.getLocalAnchorBlock());
        if (mountedShip.isEmpty()) return new EntityShipMountData();

        final Vec3d localMountPos = anchoredMount.getLocalMountPos();
        return new EntityShipMountData(mountedShip.get(), new Vec3d(
                localMountPos.x,
                localMountPos.y + ridingEntity.getMountedYOffset() + entity.getYOffset(),
                localMountPos.z
        ));
    }

    public static void fixEntityToShip(Entity toFix, Vector3dc posInLocal, PhysicsObject mountingShip) {
        World world = mountingShip.getWorld();
        EntityMountable entityMountable = new EntityMountable(world, JOML.toMinecraft(posInLocal),
                CoordinateSpaceType.SUBSPACE_COORDINATES, mountingShip.getReferenceBlockPos());
        world.spawnEntity(entityMountable);
        toFix.startRiding(entityMountable);
    }

    private static @NotNull VSWorldDataCapability getWorldDataCapability(World world) {
        VSWorldDataCapability worldData = world.getCapability(VSCapabilityRegistry.VS_WORLD_DATA, null);
        if (worldData == null) {
            // I hate it when other mods add their custom worlds without calling the forge world
            // load events, so I don't feel bad crashing the game here. Although we could also get
            // away with just adding the capability to world instead of crashing.
            throw new IllegalStateException("World " + world + " doesn't have an VSWorldDataCapability. This is wrong!");
        }

        return worldData;
    }

    public static boolean notInFakeWorldBlacklist(World world) {
        for (String worldname : VSConfig.fakeDimensionBlacklist) {
            if (world.toString().contains(worldname)) {
                return false;
            }
        }
        return true;
    }

    /**
     * This method basically grabs the {@link VSWorldDataCapability} capability from the world
     * and then returns the {@link QueryableShipData} associated with it
     *
     * @param world The world we are getting the QueryableShipData from
     * @return The QueryableShipData corresponding to the given world
     */
    public static @NotNull QueryableShipData getQueryableData(World world) {
        return getWorldDataCapability(world).get().getQueryableShipData();
    }

    /**
     * This method basically grabs the {@link VSWorldDataCapability} capability from the world
     * and then returns the {@link ShipChunkAllocator} associated with it
     *
     * @param world The world we are getting the QueryableShipData from
     * @return The QueryableShipData corresponding to the given world
     */
    public static @NotNull ShipChunkAllocator getShipChunkAllocator(World world) {
        return getWorldDataCapability(world).get().getShipChunkAllocator();
    }

    public static @NotNull WorldServerShipManager getServerShipManager(World world) {
        IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld == null) {
            throw new RuntimeException("IShipWorld doesn't appear to exist!");
        }
        return (WorldServerShipManager) shipWorld.getManager();
    }

    /**
     * Creates a new ShipIndexedData based on the inputs provided by the physics infuser block.
     */
    public static @NotNull ShipData createNewShip(World world, BlockPos physInfuserPos) {
        String name = NounListNameGenerator.getInstance().generateName();
        UUID shipID = UUID.randomUUID();
        // Create ship chunk claims
        VSChunkClaim chunkClaim = ValkyrienUtils.getShipChunkAllocator(world).allocateNextChunkClaim();
        Vector3dc centerOfMassInitial = VSMath.toVector3d(chunkClaim.getRegionCenter());
        Vector3dc shipPosInitial = VSMath.toVector3d(physInfuserPos);
        ShipTransform initial = new ShipTransform(shipPosInitial, centerOfMassInitial);
        AxisAlignedBB axisAlignedBB = new AxisAlignedBB(shipPosInitial.x(), shipPosInitial.y(),
            shipPosInitial.z(), shipPosInitial.x(), shipPosInitial.y(), shipPosInitial.z());
        return ShipData.createData(QueryableShipData.get(world).allShips(),
            name, chunkClaim, shipID, initial, axisAlignedBB);
    }

    public static @NotNull Iterable<PhysicsObject> getPhysosLoadedInWorld(World world) {
        IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld == null) {
            throw new RuntimeException("IShipWorld doesn't appear to exist!");
        }
        return shipWorld.getManager().getAllLoadedPhysObj();
    }

    public static void assembleShipAsOrderedByPlayer(World world, @Nullable EntityPlayerMP creator,
                                              BlockPos physicsInfuserPos, BlockFinder.BlockFinderType blockFinderType) {
        if (world.isRemote) {
            throw new IllegalStateException("This method cannot be invoked on client side!");
        }
        if (!(world instanceof WorldServer)) {
            throw new IllegalStateException("The world " + world + " wasn't an instance of WorldServer");
        }

        // Create the ship data that we will use to make the ship with later.
        ShipData shipData = createNewShip(world, physicsInfuserPos);

        // Queue the ship spawn operation
        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        if (!(physObjectWorld instanceof WorldServerShipManager serverShipManager)) return;
        serverShipManager.queueShipSpawn(shipData, physicsInfuserPos, blockFinderType);
    }

    public static @Nullable IPhysObjectWorld getPhysObjWorld(@Nullable World world) {
        if (world == null) return null;
        IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld == null) return null;
        return shipWorld.getManager();
    }

    /**
     * Applies the given transform matrix to the position/velocity/look of the given entity.
     * @param transform The transform matrix to be applied.
     * @param entity The entity that will be transformed.
     */
    public static void transformEntity(final Matrix4dc transform, final Entity entity, final boolean transformEntityBoundingBox) {
        Vec3d entityLookMc = entity.getLook(1.0F);

        Vector3d entityPos = new Vector3d(entity.posX, entity.posY, entity.posZ);
        Vector3d entityLook = new Vector3d(entityLookMc.x, entityLookMc.y, entityLookMc.z);
        Vector3d entityMotion = new Vector3d(entity.motionX, entity.motionY, entity.motionZ);

        if (entity instanceof EntityFireball) {
            EntityFireball ball = (EntityFireball) entity;
            entityMotion.x = ball.accelerationX;
            entityMotion.y = ball.accelerationY;
            entityMotion.z = ball.accelerationZ;
        }

        transform.transformPosition(entityPos);
        transform.transformDirection(entityLook);
        transform.transformDirection(entityMotion);

        entityLook.normalize();

        // This is correct, works properly when tested with cows
        if (entity instanceof EntityLiving) {
            EntityLiving living = (EntityLiving) entity;
            living.rotationYawHead = entity.rotationYaw;
            living.prevRotationYawHead = entity.rotationYaw;
        }

        // Get the player pitch/yaw from the look vector
        final Tuple<Double, Double> pitchYawTuple = VSMath.getPitchYawFromVector(new Vector3d(entityLook.x, entityLook.y, entityLook.z));
        entity.rotationPitch = pitchYawTuple.getFirst().floatValue();
        entity.rotationYaw = pitchYawTuple.getSecond().floatValue();

        if (entity instanceof EntityFireball) {
            EntityFireball ball = (EntityFireball) entity;
            ball.accelerationX = entityMotion.x;
            ball.accelerationY = entityMotion.y;
            ball.accelerationZ = entityMotion.z;
        }

        entity.motionX = entityMotion.x;
        entity.motionY = entityMotion.y;
        entity.motionZ = entityMotion.z;

        if (transformEntityBoundingBox) {
            // Transform the bounding box too
            final AxisAlignedBB oldBB = entity.getEntityBoundingBox();
            final TransformedAABB newBBPoly = new TransformedAABB(oldBB, transform);
            final AxisAlignedBB newBB = newBBPoly.getEnclosedAABB();

            final double oldBBSize = (oldBB.maxX - oldBB.minX) * (oldBB.maxY - oldBB.minY) * (oldBB.maxZ - oldBB.minZ);
            final double newBBSize = (newBB.maxX - newBB.minX) * (newBB.maxY - newBB.minY) * (newBB.maxZ - newBB.minZ);
            final double scaleFactor = Math.pow(oldBBSize / newBBSize, 1.0 / 3.0);

            // Scale the bounding box such that the new bounding box is the same size as the old bounding box.
            final AxisAlignedBB newBBScaled = newBB.grow(
                    (scaleFactor - 1) * (newBB.maxX - newBB.minX) / 2.0,
                    (scaleFactor - 1) * (newBB.maxY - newBB.minY) / 2.0,
                    (scaleFactor - 1) * (newBB.maxZ - newBB.minZ) / 2.0
            );

            entity.setPosition(entityPos.x, entityPos.y, entityPos.z);
            entity.setEntityBoundingBox(newBBScaled);
        }
        else {
            // Just use the regular bounding box
            entity.setPosition(entityPos.x, entityPos.y, entityPos.z);
        }
    }

    /**
     * Used for bad code that tries reading tile entities from a non game thread. But hey it works (mostly).
     */
    @Nullable
    public static TileEntity getTileEntitySafe(World world, BlockPos pos) {
        return world.getChunk(pos).getTileEntity(pos, Chunk.EnumCreateEntityType.CHECK);
    }

    @Nullable
    public static ShipData getLastShipTouchedByEntity(final Entity entity) {
        IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
        if (draggable == null) return null;
        return draggable.getLastTouchedShip();
    }

    /**
     * Formats a force magnitude using the next SI unit whenever its absolute value reaches four digits
     */
    @NotNull
    public static String formatMagnitude(double magnitude) {
        return formatMagnitude(magnitude, 0);
    }

    @NotNull
    private static String formatMagnitude(double magnitude, int unitIndex) {
        if ((magnitude <= -1000D || magnitude >= 1000D) && unitIndex < 8) {
            return formatMagnitude(magnitude / 1000D, unitIndex + 1);
        }

        String unit = switch (unitIndex) {
            case 1 -> "kN";
            case 2 -> "MN";
            case 3 -> "GN";
            case 4 -> "TN";
            case 5 -> "PN";
            case 6 -> "EN";
            case 7 -> "ZN";
            case 8 -> "YN";
            default -> "N";
        };
        DecimalFormat magnitudeFormat = new DecimalFormat("0.##", DecimalFormatSymbols.getInstance(Locale.ROOT));
        return magnitudeFormat.format(magnitude) + " " + unit;
    }
}
