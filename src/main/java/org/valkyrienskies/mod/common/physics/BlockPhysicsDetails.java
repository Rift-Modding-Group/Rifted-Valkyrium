package org.valkyrienskies.mod.common.physics;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.block.IBlockBuoyancyProvider;
import org.valkyrienskies.mod.common.block.IBlockForceProvider;
import org.valkyrienskies.mod.common.block.IBlockTorqueProvider;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BlockPhysicsDetails {
    /**
     * Blocks mapped to their mass.
     */
    private static final Map<Block, Mass> blockToMass = new HashMap<>();
    /**
     * Materials mapped to their mass.
     */
    private static final Map<Material, Mass> materialMass = new HashMap<>();
    /**
     * Blocks that should not be infused with physics.
     */
    public static final ArrayList<Block> blocksToNotPhysicsInfuse = new ArrayList<>();

    static {
        generateBlockMasses();
        generateMaterialMasses();
        generateBlocksToNotPhysicsInfuse();

        VSConfig.registerSyncEvent(BlockPhysicsDetails::onSync);
        onSync();
    }

    private static void onSync() {
        blockToMass.clear();
        generateBlockMasses();
        if (VSConfig.blockMass == null) return;
        for (String entry : VSConfig.blockMass) {
            applyConfiguredBlockMass(entry);
        }
    }

    //helper for applying block mass from config
    private static void applyConfiguredBlockMass(String entry) {
        if (entry == null || entry.trim().isEmpty()) return;

        String[] split = entry.split("=", 2);
        if (split.length != 2) {
            ValkyrienSkiesMod.LOGGER.warn("Ignoring invalid blockMass entry '{}'; expected modid:block=MASS", entry);
            return;
        }

        String blockName = normalizeBlockName(split[0]);
        Block block = Block.getBlockFromName(blockName);
        if (block == null) {
            ValkyrienSkiesMod.LOGGER.warn("Ignoring blockMass entry '{}'; unknown block '{}'", entry, blockName);
            return;
        }

        try {
            Mass mass = Mass.valueOf(split[1].trim().toUpperCase(Locale.ROOT));
            blockToMass.put(block, mass);
        }
        catch (IllegalArgumentException ex) {
            ValkyrienSkiesMod.LOGGER.warn("Ignoring blockMass entry '{}'; unknown mass '{}'", entry, split[1].trim());
        }
    }

    private static String normalizeBlockName(String blockName) {
        String normalized = blockName.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
        return normalized;
    }

    private static void generateMaterialMasses() {
        materialMass.put(Material.AIR, Mass.NONE);
        materialMass.put(Material.ANVIL, Mass.HEAVY);
        materialMass.put(Material.BARRIER, Mass.NONE);
        materialMass.put(Material.CACTUS, Mass.VERY_LIGHT);
        materialMass.put(Material.CAKE, Mass.NONE);
        materialMass.put(Material.CARPET, Mass.NONE);
        materialMass.put(Material.CIRCUITS, Mass.NONE);
        materialMass.put(Material.CLAY, Mass.HEAVY);
        materialMass.put(Material.CLOTH, Mass.NONE);
        materialMass.put(Material.CORAL, Mass.HEAVY);
        materialMass.put(Material.CRAFTED_SNOW, Mass.VERY_LIGHT);
        materialMass.put(Material.DRAGON_EGG, Mass.VERY_LIGHT);
        materialMass.put(Material.FIRE, Mass.NONE);
        materialMass.put(Material.GLASS, Mass.VERY_LIGHT);
        materialMass.put(Material.GOURD, Mass.VERY_LIGHT);
        materialMass.put(Material.GRASS, Mass.VERY_LIGHT);
        materialMass.put(Material.GROUND, Mass.VERY_LIGHT);
        materialMass.put(Material.ICE, Mass.LIGHT);
        materialMass.put(Material.IRON, Mass.HEAVY);
        materialMass.put(Material.LAVA, Mass.VERY_LIGHT);
        materialMass.put(Material.LEAVES, Mass.NONE);
        materialMass.put(Material.PACKED_ICE, Mass.VERY_LIGHT);
        materialMass.put(Material.PISTON, Mass.VERY_LIGHT);
        materialMass.put(Material.PLANTS, Mass.NONE);
        materialMass.put(Material.PORTAL, Mass.NONE);
        materialMass.put(Material.REDSTONE_LIGHT, Mass.NONE);
        materialMass.put(Material.ROCK, Mass.LIGHT);
        materialMass.put(Material.SAND, Mass.VERY_LIGHT);
        materialMass.put(Material.SNOW, Mass.VERY_LIGHT);
        materialMass.put(Material.SPONGE, Mass.VERY_LIGHT);
        materialMass.put(Material.STRUCTURE_VOID, Mass.VERY_LIGHT);
        materialMass.put(Material.TNT, Mass.VERY_LIGHT);
        materialMass.put(Material.VINE, Mass.VERY_LIGHT);
        materialMass.put(Material.WATER, Mass.VERY_LIGHT);
        materialMass.put(Material.WEB, Mass.NONE);
        materialMass.put(Material.WOOD, Mass.VERY_LIGHT);
    }

    private static void generateBlockMasses() {
        blockToMass.put(Blocks.AIR, Mass.NONE);
        blockToMass.put(Blocks.FIRE, Mass.NONE);
        blockToMass.put(Blocks.FLOWING_WATER, Mass.NONE);
        blockToMass.put(Blocks.FLOWING_LAVA, Mass.NONE);
        blockToMass.put(Blocks.WATER, Mass.NONE);
        blockToMass.put(Blocks.LAVA, Mass.NONE);
        blockToMass.put(Blocks.BEDROCK, Mass.VERY_HEAVY);

        blockToMass.put(ValkyrienSkiesControl.INSTANCE.vsControlBlocks.gyroscopeDampener, Mass.NONE);
        blockToMass.put(ValkyrienSkiesControl.INSTANCE.vsControlBlocks.gyroscopeStabilizer, Mass.NONE);
        blockToMass.put(ValkyrienSkiesControl.INSTANCE.vsControlBlocks.buoyancyTank, Mass.VERY_LIGHT);
        blockToMass.put(ValkyrienSkiesControl.INSTANCE.vsControlBlocks.networkRelay, Mass.NONE);
    }

    private static void generateBlocksToNotPhysicsInfuse() {
        blocksToNotPhysicsInfuse.add(Blocks.AIR);
        blocksToNotPhysicsInfuse.add(Blocks.WATER);
        blocksToNotPhysicsInfuse.add(Blocks.FLOWING_WATER);
        blocksToNotPhysicsInfuse.add(Blocks.LAVA);
        blocksToNotPhysicsInfuse.add(Blocks.FLOWING_LAVA);
    }

    /**
     * Get block mass, in kg.
     */
    public static double getMassFromState(IBlockState state) {
        return getMassOfBlock(state.getBlock());
    }

    private static double getMassOfBlock(Block block) {
        //prioritize block mass
        Mass blockMass = blockToMass.get(block);
        if (blockMass != null) return blockMass.mass;
        if (block instanceof BlockLiquid) return Mass.NONE.mass;

        //use material otherwise
        Material material = block.getDefaultState().getMaterial();
        return materialMass.getOrDefault(material, Mass.VERY_LIGHT).mass;
    }

    /**
     * Assigns the output parameter of toSet to be the force Vector for the given IBlockState.
     */
    public static void getForceFromState(
            IBlockState state, BlockPos pos, World world, double secondsToApply,
            PhysicsObject obj, Vector3d toSet
    ) {
        Block block = state.getBlock();
        if (block instanceof IBlockForceProvider blockForceProvider) {
            Vector3dc forceVector = blockForceProvider.getBlockForceInWorldSpace(world, pos, state, obj, secondsToApply);
            if (forceVector == null) toSet.zero();
            else {
                toSet.x = forceVector.x();
                toSet.y = forceVector.y();
                toSet.z = forceVector.z();
            }
        }
    }

    /**
     * Returns true if the given IBlockState can create force; otherwise it returns false.
     */
    public static boolean isBlockProvidingForce(IBlockState state) {
        Block block = state.getBlock();
        return block instanceof IBlockForceProvider || block instanceof IBlockTorqueProvider || block instanceof IBlockBuoyancyProvider;
    }

    /**
     * Each mass enum is to have a fixed numerical mass.
     * */
    public enum Mass {
        NONE(0),
        VERY_LIGHT(500),
        LIGHT(1000),
        HEAVY(8000),
        VERY_HEAVY(20000);

        public final int mass;

        Mass(int mass) {
            this.mass = mass;
        }
    }
}
