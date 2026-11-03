package org.valkyrienskies.mod.proxy;

import net.minecraft.block.Block;
import net.minecraftforge.fml.common.Loader;
import org.valkyrienskies.mod.client.EventsClient;
import org.valkyrienskies.mod.client.VSKeyHandler;
import org.valkyrienskies.mod.client.compat.ShipMinimapRenderer;
import org.valkyrienskies.mod.client.render.GibsModelRegistry;
import org.valkyrienskies.mod.client.render.ValkyrienSkiesOBJLoader;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.config.VSConfig;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.resources.IReloadableResourceManager;
import net.minecraft.item.Item;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.common.event.FMLPostInitializationEvent;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;

public class ClientProxy extends CommonProxy {
    // This can be called from addon code because it doesnt set namespace:id.
    public void registerItemRender(Item item, int meta) {
        ModelLoader.setCustomModelResourceLocation(item, meta, new ModelResourceLocation(item.getRegistryName(), "inventory"));
    }

    @Override
    public void preInit(FMLPreInitializationEvent e) {
        super.preInit(e);
        ModelLoaderRegistry.registerLoader(new ValkyrienSkiesOBJLoader());

        // Register events
        MinecraftForge.EVENT_BUS.register(new EventsClient());
        MinecraftForge.EVENT_BUS.register(new VSKeyHandler());

        // Register VS Minecraft resource reload listener.
        IReloadableResourceManager mcResourceManager = (IReloadableResourceManager) Minecraft.getMinecraft().getResourceManager();

        // When Minecraft reloads resources tell GibsModelRegistry to delete all its caches.
        mcResourceManager.registerReloadListener(GibsModelRegistry::onResourceManagerReload);

        //delete minimap cache too when reloading le textures
        if (Loader.isModLoaded("xaerominimap") || Loader.isModLoaded("xaeroworldmap") || Loader.isModLoaded("journeymap")) {
            mcResourceManager.registerReloadListener(manager -> ShipMinimapRenderer.INSTANCE.clear());
        }
    }

    @Override
    public void postInit(FMLPostInitializationEvent e) {
        super.postInit(e);
        Minecraft.getMinecraft().getFramebuffer().enableStencil();

        if (VSConfig.disableLegacyContent) {
            return;
        }
        registerBlockItem(ValkyrienSkiesMod.INSTANCE.captainsChair);
        registerBlockItem(ValkyrienSkiesMod.INSTANCE.passengerChair);
    }

    // Registers the inventory model for the ItemBlock of "toRegister"
    private static void registerBlockItem(Block toRegister) {
        Item item = Item.getItemFromBlock(toRegister);
        Minecraft.getMinecraft()
                .getRenderItem()
                .getItemModelMesher()
                .register(item, 0, new ModelResourceLocation(
                        ValkyrienSkiesMod.MOD_ID + ":" + item.getTranslationKey()
                                .substring(5), "inventory"));
    }
}
