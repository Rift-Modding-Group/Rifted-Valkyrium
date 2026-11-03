package org.valkyrienskies.mod.common.physics;

import net.minecraft.block.Block;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.material.Material;
import net.minecraft.block.state.IBlockState;
import net.minecraft.init.Blocks;
import org.jspecify.annotations.NonNull;
import org.valkyrienskies.mod.common.annotation.LegacyContent;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.block.IBlockForceProvider;
import org.valkyrienskies.mod.common.block.IBlockTorqueProvider;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.BlockMass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class BlockPhysicsDetails {
    /**
     * Blocks mapped to their BlockMass.
     */
    private static final Map<Block, BlockMass> blockToMass = new HashMap<>();
    /**
     * Block states mapped to their metadata-specific mass.
     */
    private static final Map<IBlockState, BlockMass> blockStateToMass = new HashMap<>();
    /**
     * Materials mapped to their BlockMass.
     */
    private static final Map<Material, BlockMass> materialBlockMass = new HashMap<>();
    /**
     * Blocks that should not be infused with physics.
     */
    public static final ArrayList<Block> blocksToNotPhysicsInfuse = new ArrayList<>();

    static {
        generateBlockMasses();
        generateLegacyBlockMasses();
        generateMaterialMasses();
        generateBlocksToNotPhysicsInfuse();

        VSConfig.registerSyncEvent(BlockPhysicsDetails::reloadBlockMasses);
        reloadBlockMasses();
    }

    private static void reloadBlockMasses() {
        blockToMass.clear();
        blockStateToMass.clear();
        generateBlockMasses();
        generateLegacyBlockMasses();
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
            ValkyrienSkiesMod.LOGGER.warn(
                    "Ignoring invalid blockMass entry '{}'; expected modid:block[:metadata]=MASS", entry
            );
            return;
        }

        BlockMass mass;
        try {
            mass = BlockMass.valueOf(split[1].trim().toUpperCase(Locale.ROOT));
        }
        catch (IllegalArgumentException ex) {
            ValkyrienSkiesMod.LOGGER.warn("Ignoring blockMass entry '{}'; unknown mass '{}'", entry, split[1].trim());
            return;
        }

        try {
            applyConfiguredBlockMass(normalizeBlockName(split[0]), mass, entry);
        }
        catch (IllegalArgumentException ex) {
            ValkyrienSkiesMod.LOGGER.warn("Ignoring blockMass entry '{}'; invalid block selector", entry);
        }
    }

    private static void applyConfiguredBlockMass(String blockSelector, BlockMass mass, String entry) {
        Block block = null;
        for (Block registeredBlock : Block.REGISTRY) {
            if (ValkyrienUtils.blockMatchesString(registeredBlock, blockSelector)) {
                block = registeredBlock;
                break;
            }
        }

        if (block == null) {
            ValkyrienSkiesMod.LOGGER.warn("Ignoring blockMass entry '{}'; unknown block selector '{}'", entry, blockSelector);
            return;
        }

        if (!hasMetadata(blockSelector)) {
            blockToMass.put(block, mass);
            return;
        }

        boolean matchedState = false;
        for (IBlockState blockState : block.getBlockState().getValidStates()) {
            if (!ValkyrienUtils.blockMatchesString(blockState, blockSelector)) continue;
            blockStateToMass.put(blockState, mass);
            matchedState = true;
        }

        if (!matchedState) {
            ValkyrienSkiesMod.LOGGER.warn("Ignoring blockMass entry '{}'; no state has the requested metadata", entry);
        }
    }

    private static boolean hasMetadata(String blockSelector) {
        int namespaceSeparator = blockSelector.indexOf(':');
        return blockSelector.indexOf(':', namespaceSeparator + 1) >= 0;
    }

    private static String normalizeBlockName(String blockName) {
        String normalized = blockName.trim().toLowerCase(Locale.ROOT);
        if (!normalized.contains(":")) normalized = "minecraft:" + normalized;
        return normalized;
    }

    private static void generateMaterialMasses() {
        materialBlockMass.put(Material.ANVIL, BlockMass.HEAVY);
        materialBlockMass.put(Material.BARRIER, BlockMass.NEGLIGIBLE);
        materialBlockMass.put(Material.CACTUS, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.CAKE, BlockMass.NEGLIGIBLE);
        materialBlockMass.put(Material.CARPET, BlockMass.NEGLIGIBLE);
        materialBlockMass.put(Material.CIRCUITS, BlockMass.NEGLIGIBLE);
        materialBlockMass.put(Material.CLAY, BlockMass.HEAVY);
        materialBlockMass.put(Material.CLOTH, BlockMass.NEGLIGIBLE);
        materialBlockMass.put(Material.CORAL, BlockMass.HEAVY);
        materialBlockMass.put(Material.CRAFTED_SNOW, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.DRAGON_EGG, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.FIRE, BlockMass.NEGLIGIBLE);
        materialBlockMass.put(Material.GLASS, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.GOURD, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.GRASS, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.GROUND, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.ICE, BlockMass.LIGHT);
        materialBlockMass.put(Material.IRON, BlockMass.HEAVY);
        materialBlockMass.put(Material.LAVA, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.LEAVES, BlockMass.NEGLIGIBLE);
        materialBlockMass.put(Material.PACKED_ICE, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.PISTON, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.PLANTS, BlockMass.NEGLIGIBLE);
        materialBlockMass.put(Material.PORTAL, BlockMass.NEGLIGIBLE);
        materialBlockMass.put(Material.REDSTONE_LIGHT, BlockMass.NEGLIGIBLE);
        materialBlockMass.put(Material.ROCK, BlockMass.LIGHT);
        materialBlockMass.put(Material.SAND, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.SNOW, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.SPONGE, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.STRUCTURE_VOID, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.TNT, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.VINE, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.WATER, BlockMass.VERY_LIGHT);
        materialBlockMass.put(Material.WEB, BlockMass.NEGLIGIBLE);
        materialBlockMass.put(Material.WOOD, BlockMass.VERY_LIGHT);
    }

    private static void generateBlockMasses() {
        blockToMass.put(Blocks.FIRE, BlockMass.NEGLIGIBLE);
        blockToMass.put(Blocks.FLOWING_WATER, BlockMass.NEGLIGIBLE);
        blockToMass.put(Blocks.FLOWING_LAVA, BlockMass.NEGLIGIBLE);
        blockToMass.put(Blocks.WATER, BlockMass.NEGLIGIBLE);
        blockToMass.put(Blocks.LAVA, BlockMass.NEGLIGIBLE);
        blockToMass.put(Blocks.BEDROCK, BlockMass.VERY_HEAVY);
    }

    @LegacyContent
    private static void generateLegacyBlockMasses() {
        blockToMass.put(ValkyrienSkiesControl.INSTANCE.vsControlBlocks.gyroscopeDampener, BlockMass.NEGLIGIBLE);
        blockToMass.put(ValkyrienSkiesControl.INSTANCE.vsControlBlocks.gyroscopeStabilizer, BlockMass.NEGLIGIBLE);
        blockToMass.put(ValkyrienSkiesControl.INSTANCE.vsControlBlocks.buoyancyTank, BlockMass.VERY_LIGHT);
        blockToMass.put(ValkyrienSkiesControl.INSTANCE.vsControlBlocks.networkRelay, BlockMass.NEGLIGIBLE);
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
    public static double getMassFromState(@NonNull IBlockState state) {
        //special case for air, it gets no mass at all
        if (state.getMaterial() == Material.AIR) return 0;

        BlockMass stateMass = blockStateToMass.get(state);
        if (stateMass != null) return stateMass.mass;
        return getMassOfBlock(state.getBlock());
    }

    private static double getMassOfBlock(Block block) {
        //prioritize block mass
        BlockMass blockMass = blockToMass.get(block);
        if (blockMass != null) return blockMass.mass;
        if (block instanceof BlockLiquid) return BlockMass.NEGLIGIBLE.mass;

        //use material otherwise
        Material material = block.getDefaultState().getMaterial();
        return materialBlockMass.getOrDefault(material, BlockMass.NEGLIGIBLE).mass;
    }

    /**
     * Returns true if the given IBlockState can create a force vector; otherwise it returns false.
     */
    public static boolean isBlockProvidingForce(IBlockState state) {
        Block block = state.getBlock();
        return block instanceof IBlockForceProvider || block instanceof IBlockTorqueProvider;
    }
}
