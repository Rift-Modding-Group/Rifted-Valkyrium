package org.valkyrienskies.addon.world;

import net.minecraft.block.Block;
import net.minecraft.command.ServerCommandManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionType;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityInject;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityRegistry;
import net.minecraftforge.fml.common.registry.GameRegistry;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.valkyrienskies.addon.world.block.BlockValkyriumOre;
import org.valkyrienskies.addon.world.capability.ICapabilityAntiGravity;
import org.valkyrienskies.addon.world.capability.ImplCapabilityAntiGravity;
import org.valkyrienskies.addon.world.capability.StorageAntiGravity;
import org.valkyrienskies.addon.world.config.VSWorldConfig;
import org.valkyrienskies.addon.world.proxy.CommonProxyWorld;
import org.valkyrienskies.addon.world.worldgen.ValkyrienSkiesWorldGen;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.command.config.VSConfigCommandBase;

import java.util.ArrayList;
import java.util.List;

@Mod(
        modid = ValkyrienSkiesWorld.MOD_ID,
        dependencies = "required-after:" + ValkyrienSkiesMod.MOD_ID
)
@EventBusSubscriber(modid = ValkyrienSkiesWorld.MOD_ID)
public class ValkyrienSkiesWorld {
    private static final Logger LOG = LogManager.getLogger(ValkyrienSkiesWorld.class);

    public static final List<Block> BLOCKS = new ArrayList<>();
    public static final List<Item> ITEMS = new ArrayList<>();

    public static final String MOD_ID = "vs_world";

    @Mod.Instance(MOD_ID)
    public static ValkyrienSkiesWorld INSTANCE;

    @SidedProxy(
            clientSide = "org.valkyrienskies.addon.world.proxy.ClientProxyWorld",
            serverSide = "org.valkyrienskies.addon.world.proxy.CommonProxyWorld"
    )
    private static CommonProxyWorld proxy;

    @CapabilityInject(ICapabilityAntiGravity.class)
    public static Capability<ICapabilityAntiGravity> ANTI_GRAVITY_CAPABILITY;

    public Block valkyriumOre;
    public Item valkyriumCrystal;

    @Mod.EventHandler
    private void preInit(FMLPreInitializationEvent event) {
        LOG.debug("Initializing configuration.");
        runConfiguration();
        registerCapabilities();
        proxy.preInit(event);
    }

    @Mod.EventHandler
    private void init(FMLInitializationEvent event) {
        EntityRegistry.registerModEntity(
                new ResourceLocation(MOD_ID, "fall_up_block_entity"),
                EntityFallingUpBlock.class,
                "fall_up_block_entity",
                75,
                INSTANCE,
                80,
                1,
                true
        );
        GameRegistry.registerWorldGenerator(new ValkyrienSkiesWorldGen(), 1);
        proxy.init(event);
    }

    @Mod.EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @Mod.EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        ServerCommandManager manager = (ServerCommandManager) event.getServer().getCommandManager();
        manager.registerCommand(new VSConfigCommandBase("vsworldconfig", VSWorldConfig.class));
    }

    private void registerCapabilities() {
        CapabilityManager.INSTANCE.register(ICapabilityAntiGravity.class, new StorageAntiGravity(), ImplCapabilityAntiGravity::new);
    }

    /**
     * Initializes the configuration - {@link VSWorldConfig}
     */
    private void runConfiguration() {
        VSWorldConfig.sync();
    }

    @SubscribeEvent
    public static void registerBlocks(RegistryEvent.Register<Block> event) {
        INSTANCE.valkyriumOre = registerBlock(new BlockValkyriumOre());
        event.getRegistry().registerAll(BLOCKS.toArray(new Block[0]));
    }

    @SubscribeEvent
    public static void registerItems(RegistryEvent.Register<Item> event) {
        INSTANCE.valkyriumCrystal = registerItem( new ItemValkyriumCrystal());
        event.getRegistry().registerAll(ITEMS.toArray(new Item[0]));
    }

    @SubscribeEvent
    public static void registerPotions(RegistryEvent.Register<Potion> event) {
        PotionInit.registerPotions(event);
    }

    @SubscribeEvent
    public static void registerPotionTypes(RegistryEvent.Register<PotionType> event) {
        PotionInit.registerPotionTypes(event);
    }

    private static <T extends Block> T registerBlock(T block) {
        BLOCKS.add(block);
        ITEMS.add(new ItemBlock(block).setRegistryName(block.getRegistryName()));
        return block;
    }

    private static <T extends Item> T registerItem(T item) {
        ITEMS.add(item);
        return item;
    }
}
