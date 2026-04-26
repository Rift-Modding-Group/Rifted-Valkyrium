package org.valkyrienskies.addon.world.util

import net.minecraft.block.Block
import net.minecraft.block.material.Material
import net.minecraft.item.Item
import net.minecraft.item.ItemBlock
import org.valkyrienskies.addon.world.ValkyrienSkiesWorld
import org.valkyrienskies.mod.client.BaseModel
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

open class BaseBlock(name: String, mat: Material, light: Float, hasCreativeTab: Boolean) : Block(mat), BaseModel {
    override fun registerModels() {
        ValkyrienSkiesMod.proxy.registerItemRender(Item.getItemFromBlock(this), 0)
    }

    init {
        setTranslationKey(name)
        this.setRegistryName(name)
        setLightLevel(light)
        if (hasCreativeTab) {
            creativeTab = ValkyrienSkiesMod.VS_CREATIVE_TAB
        }
        ValkyrienSkiesWorld.BLOCKS.add(this)
        ValkyrienSkiesWorld.ITEMS.add(ItemBlock(this).setRegistryName(registryName))
    }
}