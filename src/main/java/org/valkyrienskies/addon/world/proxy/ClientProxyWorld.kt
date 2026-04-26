package org.valkyrienskies.addon.world.proxy

import net.minecraft.block.Block
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.block.model.ModelResourceLocation
import net.minecraft.client.renderer.entity.RenderFallingBlock
import net.minecraft.client.renderer.entity.RenderManager
import net.minecraft.item.Item
import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.client.registry.RenderingRegistry
import net.minecraftforge.fml.common.event.FMLStateEvent
import org.valkyrienskies.addon.world.EntityFallingUpBlock
import org.valkyrienskies.addon.world.ValkyrienSkiesWorld
import org.valkyrienskies.addon.world.WorldEventsClient

class ClientProxyWorld : CommonProxyWorld() {

    override fun preInit(e: FMLStateEvent) {
        super.preInit(e)
        RenderingRegistry
                .registerEntityRenderingHandler(EntityFallingUpBlock::class.java) { RenderFallingBlock(it) }
        MinecraftForge.EVENT_BUS.register(WorldEventsClient())
    }

    override fun init(e: FMLStateEvent) {
        super.init(e)
        registerBlockItem(ValkyrienSkiesWorld.INSTANCE.valkyriumOre)
    }

    override fun postInit(e: FMLStateEvent) {
        super.postInit(e)
        registerItemModel(ValkyrienSkiesWorld.INSTANCE.valkyriumCrystal)
    }

    private fun registerBlockItem(toRegister: Block?) {
        val item = Item.getItemFromBlock(toRegister)
        Minecraft.getMinecraft()
                .renderItem
                .itemModelMesher
                .register(item, 0, ModelResourceLocation(
                        ValkyrienSkiesWorld.MOD_ID + ":" + item.translationKey
                                .substring(5), "inventory"))
    }

    private fun registerItemModel(toRegister: Item) {
        val renderItem = Minecraft.getMinecraft()
                .renderItem
        renderItem.itemModelMesher
                .register(toRegister, 0, ModelResourceLocation(
                        ValkyrienSkiesWorld.MOD_ID + ":" +
                                toRegister.translationKey.substring(5), "inventory")
                )
    }
}