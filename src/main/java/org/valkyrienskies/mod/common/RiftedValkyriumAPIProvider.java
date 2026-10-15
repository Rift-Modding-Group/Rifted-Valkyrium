package org.valkyrienskies.mod.common;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.Entity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.LoaderState;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3d;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.VSWorldDataCapability;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.block_relocation.BlockFinder;
import org.valkyrienskies.mod.common.ships.chunk_claims.ShipChunkAllocator;
import org.valkyrienskies.mod.common.ships.chunk_claims.VSChunkClaim;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.WorldServerShipManager;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.mod.common.util.names.NounListNameGenerator;
import valkyrienwarfare.api.*;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
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
    public Optional<IShipData> getActiveShipOfEntity(Entity entity) {
        ShipData lastTouchedShip = EntityDraggable.getActiveShip(entity);
        return Optional.ofNullable(lastTouchedShip);
    }

    @Override
    public void setBlockMass(IBlockState blockState, BlockMass blockMass) {
        Block block = blockState.getBlock();
        ResourceLocation registryName = requireRegistryName(block);
        addBlockMass(registryName + ":" + block.getMetaFromState(blockState), blockMass);
    }

    @Override
    public void setBlockMass(Block block, BlockMass blockMass) {
        addBlockMass(requireRegistryName(block).toString(), blockMass);
    }

    private void addBlockMass(String blockSelector, BlockMass blockMass) {
        if (VSConfig.blockMass != null) {
            for (String entry : VSConfig.blockMass) {
                if (entry == null) continue;
                String[] split = entry.split("=", 2);
                if (split.length != 2) continue;
                try {
                    BlockMass.valueOf(split[1].trim().toUpperCase(Locale.ROOT));
                    if (normalizeBlockSelector(split[0]).equals(normalizeBlockSelector(blockSelector))) return;
                }
                catch (IllegalArgumentException ex) {
                    continue;
                }
            }
        }

        String newEntry = blockSelector + "=" + blockMass.name();
        if (VSConfig.blockMass == null) {
            VSConfig.blockMass = new String[]{newEntry};
        }
        else {
            VSConfig.blockMass = Arrays.copyOf(VSConfig.blockMass, VSConfig.blockMass.length + 1);
            VSConfig.blockMass[VSConfig.blockMass.length - 1] = newEntry;
        }

        if (Loader.instance().hasReachedState(LoaderState.AVAILABLE)) {
            VSConfig.sync();
        }
    }

    private String normalizeBlockSelector(String blockSelector) {
        String normalized = blockSelector.trim().toLowerCase(Locale.ROOT);
        int namespaceSeparator = normalized.indexOf(':');
        if (namespaceSeparator < 0) return "minecraft:" + normalized;
        int metadataSeparator = normalized.indexOf(':', namespaceSeparator + 1);
        if (metadataSeparator < 0) return normalized;

        String blockName = normalized.substring(0, metadataSeparator);
        int metadata = Integer.parseInt(normalized.substring(metadataSeparator + 1));
        return blockName + ":" + metadata;
    }

    @NotNull
    private ResourceLocation requireRegistryName(Block block) {
        ResourceLocation registryName = block.getRegistryName();
        if (registryName == null) throw new IllegalArgumentException("Block must have a registry name");
        return registryName;
    }

    @Override
    @NotNull
    @SuppressWarnings("deprecation")
    public IShipData createNewShip(WorldServer world, Vec3d initialPosition, AxisAlignedBB initialBounds, ShipBuildOptions shipBuildOptions) {
        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        if (!(physObjectWorld instanceof WorldServerShipManager serverShipManager)) {
            throw new IllegalStateException("Could not get server ship manager from world!");
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
                initialBounds, shipBuildOptions.getShipBlockBlacklist()
        );
        shipData.setPhysicsEnabled(shipBuildOptions.getPhysicsEnabled());

        BlockPos assemblyPosition = new BlockPos(initialPosition.x, initialPosition.y, initialPosition.z);
        serverShipManager.queueShipSpawn(shipData, assemblyPosition, BlockFinder.BlockFinderType.FIND_ALLOWED_BLOCKS);
        return shipData;
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
    public Optional<IShipData> getShipByName(World world, String name) {
        return this.getAllShips(world).stream().filter(shipData -> shipData.getName().equals(name)).findFirst();
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
