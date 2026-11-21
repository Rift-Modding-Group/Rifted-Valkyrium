package org.valkyrienskies.mod.common;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.capability.ship_world.IShipWorld;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.VSWorldDataCapability;
import org.valkyrienskies.mod.common.entity.EntityMountable;
import org.valkyrienskies.mod.common.physics.BlockPhysicsDetails;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ShipBlockBlacklistRegistry;
import org.valkyrienskies.mod.common.ships.ShipDataMethods;
import org.valkyrienskies.mod.common.ships.ShipEntityAttachment;
import org.valkyrienskies.mod.common.ships.block_relocation.BlockFinder;
import org.valkyrienskies.mod.common.ships.chunk_claims.ShipChunkAllocator;
import org.valkyrienskies.mod.common.ships.chunk_claims.VSChunkClaim;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.WorldServerShipManager;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.mod.common.util.names.NounListNameGenerator;
import valkyrienwarfare.api.*;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

@ParametersAreNonnullByDefault
public class RiftedValkyriumAPIProvider implements RiftedValkyriumAPI.Provider {
    @Override
    @NotNull
    public Optional<IPhysicsEntity> getPhysoManagingBlock(World world, BlockPos pos) {
        Optional<ShipData> shipData = this.findShipManagingBlock(world, pos);
        if (shipData.isEmpty()) return Optional.empty();

        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        if (physObjectWorld == null) {
            throw new IllegalStateException("Could not get ship manager from world!");
        }

        return shipData.map(data -> physObjectWorld.getPhysObjectFromUUID(data.getUuid()))
                .map(physicsObject -> (IPhysicsEntity) physicsObject);
    }

    @Override
    @NotNull
    public Optional<IShipData> getShipManagingBlock(World world, BlockPos pos) {
        return findShipManagingBlock(world, pos).map(shipData -> (IShipData) shipData);
    }

    @Override
    public boolean isPosInShipyard(BlockPos pos) {
        return ShipChunkAllocator.isBlockInShipyard(pos);
    }

    @Override
    @NotNull
    public Vector3dc getWorldGravity() {
        double gravityY = VSConfig.doGravity ? VSConfig.gravityVecY : 0D;
        return new Vector3d(0D, gravityY, 0D);
    }

    @Override
    @NotNull
    public Optional<IShipData> getActiveShipOfEntity(Entity entity) {
        Entity carrier = entity.getLowestRidingEntity();
        //special case for legacy chairs
        if (carrier instanceof EntityMountable mountable) {
            return mountable.getMountedShip().map(PhysicsObject::getShipData);
        }
        return Optional.ofNullable(EntityDraggable.getActiveShip(carrier));
    }

    @Override
    public void setBlockMass(IBlockState blockState, BlockMass blockMass) {
        BlockPhysicsDetails.setBlockMass(blockState, blockMass);
    }

    @Override
    public void setBlockMass(Block block, BlockMass blockMass) {
        BlockPhysicsDetails.setBlockMass(block, blockMass);
    }

    @Override
    @NotNull
    @SuppressWarnings("deprecation")
    public IShipData createNewShip(WorldServer world, Vec3d initialPosition, AxisAlignedBB initialBounds, ShipBuildOptions shipBuildOptions) {
        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        if (!(physObjectWorld instanceof WorldServerShipManager serverShipManager)) {
            throw new IllegalStateException("Could not get server ship manager from world!");
        }

        Entity peggedEntity = shipBuildOptions.getPeggedEntity();
        ShipEntityAttachment entityAttachment = null;
        if (peggedEntity != null) {
            if (peggedEntity.world != world || peggedEntity.isDead) {
                throw new IllegalArgumentException("Ship carrier must be a living entity in the same world");
            }
            Vec3d offset = shipBuildOptions.getPeggedEntityOffset();
            if (offset == null) offset = Vec3d.ZERO;
            entityAttachment = new ShipEntityAttachment(peggedEntity.getUniqueID(), offset.x, offset.y, offset.z,
                    0D, 0D, 0D, 0D, 1D, shipBuildOptions.getEntityDeathBehavior(), shipBuildOptions.getPeggedEntityLocator());
        }

        BlockPos assemblyPosition = new BlockPos(initialPosition.x, initialPosition.y, initialPosition.z);
        ShipBlockBlacklist blockBlacklist = shipBuildOptions.getShipBlockBlacklist();
        int[] maxShipBounds = shipBuildOptions.getMaxShipBounds();
        if (maxShipBounds != null) {
            AxisAlignedBB bounds = ShipDataMethods.createBuildBounds(assemblyPosition, maxShipBounds);

            // Apply the creator's blacklist inside the fixed build region.
            ShipBlockBlacklist configuredBlacklist = blockBlacklist;
            blockBlacklist = (candidateWorld, candidatePosition, state) -> {
                return !ShipDataMethods.isWithinBuildBounds(bounds, candidatePosition)
                        || configuredBlacklist.isBlacklisted(candidateWorld, candidatePosition, state);
            };
        }

        ShipBlockBlacklist assembledBlacklist = shipBuildOptions.getShipBlockAssembledBlacklist();
        String assembledBlacklistId = null;
        if (assembledBlacklist != null) {
            assembledBlacklistId = ShipBlockBlacklistRegistry.register(assembledBlacklist);
        }

        Vector3d position = new Vector3d(initialPosition.x, initialPosition.y, initialPosition.z);
        VSWorldDataCapability worldData = this.getWorldDataCapability(world);
        QueryableShipData queryableShipData = worldData.get().getQueryableShipData();
        VSChunkClaim chunkClaim = worldData.get().getShipChunkAllocator().allocateNextChunkClaim();
        BlockPos regionCenter = chunkClaim.getRegionCenter();
        Vector3d centerOfMass = new Vector3d(regionCenter.getX(), regionCenter.getY(), regionCenter.getZ());
        ShipTransform initialTransform = new ShipTransform(position, centerOfMass);

        String shipName;
        if (shipBuildOptions.getShipName() != null) shipName = shipBuildOptions.getShipName();
        else shipName = NounListNameGenerator.getInstance().generateName();

        ShipData shipData = ShipData.createData(
                queryableShipData.allShips(), shipName,
                chunkClaim, UUID.randomUUID(), initialTransform,
                initialBounds, blockBlacklist
        );
        shipData.setPhysicsEnabled(shipBuildOptions.getPhysicsEnabled());
        shipData.dontSetSpawn = shipBuildOptions.getDontSetSpawn();
        shipData.assembledBlockBlacklistId = assembledBlacklistId;
        if (maxShipBounds != null) {
            shipData.shipBounds = ShipDataMethods.createBuildBounds(regionCenter, maxShipBounds);
        }
        shipData.entityAttachment = entityAttachment;

        serverShipManager.queueShipSpawn(shipData, assemblyPosition, BlockFinder.BlockFinderType.FIND_ALLOWED_BLOCKS);
        return shipData;
    }

    @Override
    public void linkShipToEntity(WorldServer world, IShipData ship, Entity entity, Vec3d offset, ShipEntityDeathBehavior entityDeathBehavior, @org.jetbrains.annotations.Nullable String locator) {
        if (!world.isCallingFromMinecraftThread()) {
            throw new IllegalStateException("Ships must be linked on the owning world's server thread");
        }
        if (entity.world != world || entity.isDead) {
            throw new IllegalArgumentException("Ship carrier must be an alive entity in the same world");
        }
        ShipData data = this.getWorldDataCapability(world).get().getQueryableShipData().getShip(ship.getUuid())
                .orElseThrow(() -> new IllegalArgumentException("Ship does not belong to this world"));
        if (data.entityAttachment != null && data.entityAttachment.entityUuid().equals(entity.getUniqueID())) {
            // Offset updates retain the rotation reference and let the regular ship tick advance motion.
            data.entityAttachment = ShipEntityAttachment.updateOffset(data.entityAttachment, offset, entityDeathBehavior, locator);
            data.entityCarrierDead = false;
            return;
        }
        ShipEntityAttachment attachment = ShipEntityAttachment.create(entity, offset, data.getShipTransform(), entityDeathBehavior, locator);
        data.entityAttachment = attachment;
        data.entityCarrierDead = false;
        data.entityAttachmentPoint = null;
        ShipTransform transform = attachment.createTransform(entity, data.getEntityAttachmentPoint(),
                data.getShipTransform().getCenterCoord());
        data.setShipTransform(transform);
        data.getPhysicsData().setLinearVelocity(new Vector3d());
        data.getPhysicsData().setAngularVelocity(new Vector3d());
        IPhysObjectWorld shipWorld = ValkyrienUtils.getPhysObjWorld(world);
        if (shipWorld != null) {
            PhysicsObject physicsObject = shipWorld.getPhysObjectFromUUID(data.getUuid());
            if (physicsObject != null) physicsObject.entityAttachmentTransform = transform;
        }
    }


    @Override
    @Nullable
    public RayTraceResult rayTraceBlocksIgnoringCarrierShip(
            World world, Entity carrier, Vec3d start, Vec3d end,
            boolean stopOnLiquid, boolean ignoreBlocksWithoutBounds, boolean returnLastMiss
    ) {
        IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        IPhysObjectWorld manager = ValkyrienUtils.getPhysObjWorld(world);
        PhysicsObject ignored = null;
        if (manager != null) {
            for (PhysicsObject ship : manager.getAllLoadedThreadSafe()) {
                if (ship.getShipData().isPeggedTo(carrier)) {
                    ignored = ship;
                    break;
                }
            }
        }
        if (shipWorld == null || ignored == null) {
            return world.rayTraceBlocks(start, end, stopOnLiquid, ignoreBlocksWithoutBounds, returnLastMiss);
        }
        PhysicsObject previous = shipWorld.getDontInterceptShip().get();
        shipWorld.getDontInterceptShip().set(ignored);
        RayTraceResult result;
        try {
            result = world.rayTraceBlocks(start, end, stopOnLiquid, ignoreBlocksWithoutBounds, returnLastMiss);
        }
        catch (RuntimeException | Error failure) {
            if (previous == null) shipWorld.getDontInterceptShip().remove();
            else shipWorld.getDontInterceptShip().set(previous);
            throw failure;
        }
        if (previous == null) shipWorld.getDontInterceptShip().remove();
        else shipWorld.getDontInterceptShip().set(previous);
        return result;
    }

    @Override
    @NotNull
    public List<IShipData> getAllShips(World world) {
        VSWorldDataCapability worldData = this.getWorldDataCapability(world);
        return List.copyOf(worldData.get().getQueryableShipData().getShips());
    }

    @Override
    @NotNull
    public Optional<IPhysicsEntity> getPhysicsEntity(World world, IShipData shipData) {
        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        if (physObjectWorld == null) throw new IllegalStateException("Could not get ship manager from world!");
        return physObjectWorld.getAllLoadedThreadSafe().stream()
                .filter(physicsObject -> physicsObject.getUuid().equals(shipData.getUuid()))
                .map(physicsObject -> (IPhysicsEntity) physicsObject)
                .findFirst();
    }

    @Override
    @NotNull
    public Optional<IShipData> getShipByUUID(World world, UUID uuid) {
        return this.getAllShips(world).stream().filter(shipData -> shipData.getUuid().equals(uuid)).findFirst();
    }

    @Override
    @NotNull
    public List<IShipData> getShipsByName(World world, String name) {
        return this.getAllShips(world).stream().filter(shipData -> shipData.getName().equals(name)).toList();
    }

    @NotNull
    private Optional<ShipData> findShipManagingBlock(World world, BlockPos pos) {
        if (!ShipChunkAllocator.isBlockInShipyard(pos)) return Optional.empty();

        return getWorldDataCapability(world).get().getQueryableShipData().getShipFromBlock(pos);
    }

    @NotNull
    private VSWorldDataCapability getWorldDataCapability(World world) {
        VSWorldDataCapability worldData = world.getCapability(VSCapabilityRegistry.VS_WORLD_DATA, null);
        if (worldData == null) {
            throw new IllegalStateException("World " + world + " doesn't have a VSWorldDataCapability. This is wrong!");
        }

        return worldData;
    }
}
