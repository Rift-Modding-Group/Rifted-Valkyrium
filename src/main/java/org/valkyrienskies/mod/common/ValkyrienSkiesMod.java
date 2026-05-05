package org.valkyrienskies.mod.common;

import com.google.common.collect.ImmutableList;
import net.minecraft.block.Block;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.item.crafting.ShapedRecipes;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.crafting.CraftingHelper;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Loader;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.Mod.EventHandler;
import net.minecraftforge.fml.common.Mod.Instance;
import net.minecraftforge.fml.common.SidedProxy;
import net.minecraftforge.fml.common.event.FMLInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import net.minecraftforge.fml.common.event.FMLStateEvent;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.common.registry.GameRegistry;
import net.minecraftforge.fml.relauncher.Side;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.valkyrienskies.mod.client.gui.TabValkyrienSkies;
import org.valkyrienskies.mod.common.block.BlockBoatChair;
import org.valkyrienskies.mod.common.block.BlockCaptainsChair;
import org.valkyrienskies.mod.common.block.BlockPassengerChair;
import org.valkyrienskies.mod.common.block.BlockWaterPump;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.command.framework.VSCommandRegistry;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.item.ItemShipTracker;
import org.valkyrienskies.mod.common.network.MessagePlayerStoppedPiloting;
import org.valkyrienskies.mod.common.network.MessagePlayerStoppedPilotingHandler;
import org.valkyrienskies.mod.common.network.MessageStartPiloting;
import org.valkyrienskies.mod.common.network.MessageStartPilotingHandler;
import org.valkyrienskies.mod.common.network.MessageStopPiloting;
import org.valkyrienskies.mod.common.network.MessageStopPilotingHandler;
import org.valkyrienskies.mod.common.network.ShipIndexDataMessage;
import org.valkyrienskies.mod.common.network.ShipIndexDataMessageHandler;
import org.valkyrienskies.mod.common.network.ShipTransformUpdateMessage;
import org.valkyrienskies.mod.common.network.ShipTransformUpdateMessageHandler;
import org.valkyrienskies.mod.common.piloting.PilotControlsMessage;
import org.valkyrienskies.mod.common.piloting.PilotControlsMessageHandler;
import org.valkyrienskies.mod.common.piloting.PilotControlsMessageNew;
import org.valkyrienskies.mod.common.tileentity.TileEntityBoatChair;
import org.valkyrienskies.mod.common.tileentity.TileEntityCaptainsChair;
import org.valkyrienskies.mod.common.tileentity.TileEntityPassengerChair;
import org.valkyrienskies.mod.common.tileentity.TileEntityWaterPump;
import org.valkyrienskies.mod.fixes.darkness_lib_fix.VSDarknessLibAPILightProvider;
import org.valkyrienskies.mod.proxy.CommonProxy;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ForkJoinPool;
import java.util.function.Function;

@Mod(
    modid = ValkyrienSkiesMod.MOD_ID,
    useMetadata = true,
    certificateFingerprint = ValkyrienSkiesMod.MOD_FINGERPRINT
)
public class ValkyrienSkiesMod {
    public static final Logger LOGGER = LogManager.getLogger();
    // Used for registering stuff
    public static final List<Block> BLOCKS = new ArrayList<>();
    public static final List<Item> ITEMS = new ArrayList<>();

    // MOD INFO CONSTANTS
    public static final String MOD_ID = "valkyrienskies";
    static final String MOD_FINGERPRINT = "b308676914a5e7d99459c1d2fb298744387899a7";

    // MOD INSTANCE
    @Instance(MOD_ID)
    public static ValkyrienSkiesMod INSTANCE;

    @SidedProxy(
        clientSide = "org.valkyrienskies.mod.proxy.ClientProxy",
        serverSide = "org.valkyrienskies.mod.proxy.ServerProxy"
    )
    public static CommonProxy proxy;

    static final int VS_ENTITY_LOAD_DISTANCE = 128;

    /**
     * This service is directly responsible for running collision tasks.
     */
    private static ForkJoinPool physicsThreadPool = null;

    public Block captainsChair;
    public Block passengerChair;
    public Block waterPump;
    public Block boatChair;
    public Item shipTracker;
    public static SimpleNetworkWrapper physWrapperNetwork;
    public static SimpleNetworkWrapper physWrapperTransformUpdateNetwork;
    public static SimpleNetworkWrapper controlNetwork;
    public static final CreativeTabs VS_CREATIVE_TAB = new TabValkyrienSkies(MOD_ID);

    private static final List<String> MODULES = ImmutableList.of("vs_control", "vs_world");

    @EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        LOGGER.debug("Initializing configuration.");
        runConfiguration();

        LOGGER.debug("Instantiating the physics thread executor.");
        ValkyrienSkiesMod.physicsThreadPool = new ForkJoinPool(VSConfig.threadCount);

        LOGGER.debug("Initializing networks.");
        registerNetworks(event);

		VSCapabilityRegistry.registerCapabilities();
        proxy.preInit(event);

        registerItems();
		registerBlocks();

		registerDarknessLib();
    }

    private void registerDarknessLib() {
        // Register light provider with DarknessLib
        try {
            // Why yes, I am using some hacky reflection. I know theres an IMCEvent way to do it, but I can't get it to
            // work no matter what I do! So reflection it is!
            final Class clazz = Class.forName("com.shinoow.darknesslib.api.DarknessLibAPI");
            final Field instanceField = clazz.getDeclaredField("INSTANCE");
            final Field lightProvidersField = clazz.getDeclaredField("LIGHT_PROVIDERS");
            // Remove private from the fields
            instanceField.setAccessible(true);
            lightProvidersField.setAccessible(true);
            // Finally add the light provider
            final Object instance = instanceField.get(null);
            final List<Function<EntityPlayer, Integer>> lightProviders = (List<Function<EntityPlayer, Integer>>) lightProvidersField.get(instance);
            lightProviders.add(new VSDarknessLibAPILightProvider());
        }
        catch (Exception e) {}
    }

    @EventHandler
    public void init(FMLInitializationEvent event) {
        LOGGER.info("Valkyrien Skies Initialization: We are running on {} threads; 4 or more "
            + "is recommended!", Runtime.getRuntime().availableProcessors());
        proxy.init(event);

        isSpongePresent = Loader.isModLoaded("spongeforge");
    }

    @EventHandler
    public void postInit(FMLPostInitializationEvent event) {
        proxy.postInit(event);
    }

    @EventHandler
    public void serverStart(FMLServerStartingEvent event) {
        MinecraftServer server = event.getServer();
        VSCommandRegistry.registerCommands(server);
    }

    private void registerNetworks(FMLStateEvent event) {
        physWrapperNetwork = NetworkRegistry.INSTANCE.newSimpleChannel("valkyrien_skies");
        physWrapperNetwork.registerMessage(ShipIndexDataMessageHandler.class,
                ShipIndexDataMessage.class, 0, Side.CLIENT);

        controlNetwork = NetworkRegistry.INSTANCE.newSimpleChannel("valkyrien_piloting");
        controlNetwork.registerMessage(PilotControlsMessageHandler.class,
                PilotControlsMessage.class, 0, Side.SERVER);
        controlNetwork.registerMessage(PilotControlsMessageNew.Handler.class,
                PilotControlsMessageNew.class, 1, Side.SERVER);
        controlNetwork.registerMessage(MessageStartPilotingHandler.class,
                MessageStartPiloting.class, 2, Side.CLIENT);
        controlNetwork.registerMessage(MessageStopPilotingHandler.class,
                MessageStopPiloting.class, 3, Side.CLIENT);
        controlNetwork.registerMessage(MessagePlayerStoppedPilotingHandler.class,
                MessagePlayerStoppedPiloting.class, 4, Side.SERVER);

        physWrapperTransformUpdateNetwork = NetworkRegistry.INSTANCE.newSimpleChannel("vs_ship_transforms");
        physWrapperTransformUpdateNetwork.registerMessage(ShipTransformUpdateMessageHandler.class,
                ShipTransformUpdateMessage.class, 0, Side.CLIENT);
    }

    void registerRecipes(RegistryEvent.Register<IRecipe> event) {
        if (!VSConfig.chairRecipes) return;

        registerRecipe(event, "recipe_captains_chair", new ItemStack(captainsChair),
                    "SLS",
                    "VWV",
                    " S ",
                    'S', Items.STICK,
                    'L', Items.LEATHER,
                    'W', Item.getItemFromBlock(Blocks.LOG),
                    'V', Items.DIAMOND);

        registerRecipe(event, "recipe_passenger_chair", new ItemStack(passengerChair),
                    "SLS",
                    "PWP",
                    " S ",
                    'S', Items.STICK,
                    'L', Items.LEATHER,
                    'W', Item.getItemFromBlock(Blocks.LOG),
                    'P', Item.getItemFromBlock(Blocks.PLANKS));
    }

    private static void registerRecipe(RegistryEvent.Register<IRecipe> event,
        String registryName, ItemStack out, Object... in) {
        CraftingHelper.ShapedPrimer primer = CraftingHelper.parseShaped(in);
        event.getRegistry()
            .register(new ShapedRecipes(
                ValkyrienSkiesMod.MOD_ID, primer.width, primer.height, primer.input, out)
                .setRegistryName(ValkyrienSkiesMod.MOD_ID, registryName));
    }

    /**
     * Initializes the configuration - {@link VSConfig}
     */
    private void runConfiguration() {
        VSConfig.sync();
    }

    private void registerTileEntities() {
        GameRegistry.registerTileEntity(TileEntityCaptainsChair.class,
                new ResourceLocation(MOD_ID, "tile_captains_chair"));
        GameRegistry.registerTileEntity(TileEntityPassengerChair.class,
                new ResourceLocation(MOD_ID, "tile_passenger_chair"));
        GameRegistry.registerTileEntity(TileEntityWaterPump.class,
                new ResourceLocation(MOD_ID, "tile_water_pump"));
        GameRegistry.registerTileEntity(TileEntityBoatChair.class,
                new ResourceLocation(MOD_ID, "tile_boat_chair"));
    }

    private void registerBlocks() {
        this.captainsChair = registerBlock(new BlockCaptainsChair());
        this.passengerChair = registerBlock(new BlockPassengerChair());
        this.waterPump = registerBlock(new BlockWaterPump());
        this.boatChair = registerBlock(new BlockBoatChair());


        this.registerTileEntities();
    }

    private Block registerBlock(Block block) {
        ValkyrienSkiesMod.BLOCKS.add(block);
        ValkyrienSkiesMod.ITEMS.add(new ItemBlock(block).setRegistryName(block.getRegistryName()));
        return block;
    }

    private void registerItems() {
        this.shipTracker = new ItemShipTracker("vs_ship_tracker", true);
    }

    private static boolean isSpongePresent = false;

    public static ForkJoinPool getPhysicsThreadPool() {
        return physicsThreadPool;
    }

    public static boolean isSpongePresent() {
        return isSpongePresent;
    }
}
