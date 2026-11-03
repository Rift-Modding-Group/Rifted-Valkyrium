package org.valkyrienskies.mod.common;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import javax.annotation.Nonnull;

import org.valkyrienskies.mod.common.annotation.LegacyContent;
import org.valkyrienskies.mod.client.BaseModel;
import org.valkyrienskies.mod.common.entity.EntityMountable;
import org.valkyrienskies.mod.common.entity.EntityMountableChair;

import net.minecraft.block.Block;
import net.minecraft.item.Item;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.ModelRegistryEvent;
import net.minecraftforge.event.RegistryEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.registry.EntityEntry;
import net.minecraftforge.fml.common.registry.EntityEntryBuilder;

@Mod.EventBusSubscriber(modid = ValkyrienSkiesMod.MOD_ID)
public class RegisterEvents {

    @LegacyContent
    @SubscribeEvent
    public static void registerBlocks(@Nonnull RegistryEvent.Register<Block> event) {
        ValkyrienSkiesMod.LOGGER.debug("Registering blocks");
        Block[] blockArray = ValkyrienSkiesMod.BLOCKS.toArray(new Block[0]);
        event.getRegistry().registerAll(blockArray);
    }

    @LegacyContent
    @SubscribeEvent
    public static void registerItems(@Nonnull RegistryEvent.Register<Item> event) {
        ValkyrienSkiesMod.LOGGER.debug("Registering items");
        event.getRegistry().registerAll(ValkyrienSkiesMod.ITEMS.toArray(new Item[0]));
    }

    @LegacyContent
    @SubscribeEvent
    public static void registerRecipes(@Nonnull RegistryEvent.Register<IRecipe> event) {
        ValkyrienSkiesMod.INSTANCE.registerRecipes(event);
    }

    @LegacyContent
    @SubscribeEvent
    public static void onModelRegister(ModelRegistryEvent event) {
        for (Item item : ValkyrienSkiesMod.ITEMS) {
            if (item instanceof BaseModel) {
                ((BaseModel) item).registerModels();
            }
        }

        for (Block block : ValkyrienSkiesMod.BLOCKS) {
            if (block instanceof BaseModel) {
                ((BaseModel) block).registerModels();
            }
        }
    }

    @LegacyContent
    public static void registerLegacyModels() {
        registerBlockItem(ValkyrienSkiesMod.INSTANCE.captainsChair);
        registerBlockItem(ValkyrienSkiesMod.INSTANCE.passengerChair);
    }

    @LegacyContent
    private static void registerBlockItem(Block block) {
        Item item = Item.getItemFromBlock(block);
        Minecraft.getMinecraft().getRenderItem().getItemModelMesher().register(item, 0,
                new ModelResourceLocation(ValkyrienSkiesMod.MOD_ID + ":"
                        + item.getTranslationKey().substring(5), "inventory"));
    }

    /**
     * This method will be called by Forge when it is time for the mod to register its entity
     * entries.
     */
    @SubscribeEvent
    public static void onRegisterEntitiesEvent(
            @Nonnull RegistryEvent.Register<EntityEntry> event) {
        ResourceLocation entityMountable = new ResourceLocation(ValkyrienSkiesMod.MOD_ID,
                "entity_mountable");
        event.getRegistry().register(
                EntityEntryBuilder.create()
                        .entity(EntityMountable.class)
                        .id(entityMountable, 0)
                        .name(entityMountable.getPath())
                        .tracker(ValkyrienSkiesMod.VS_ENTITY_LOAD_DISTANCE, 1, false)
                        .build()
        );
        EntityMountableChair.registerEntity(event, 1, ValkyrienSkiesMod.VS_ENTITY_LOAD_DISTANCE);
    }
}
