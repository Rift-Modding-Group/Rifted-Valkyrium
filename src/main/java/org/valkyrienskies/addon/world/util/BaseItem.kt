package org.valkyrienskies.addon.world.util

import net.minecraft.item.Item
import org.valkyrienskies.addon.world.ValkyrienSkiesWorld
import org.valkyrienskies.mod.client.BaseModel
import org.valkyrienskies.mod.common.ValkyrienSkiesMod

// Addons need to provide their own copies of this class.
open class BaseItem(name: String, hasCreativeTab: Boolean) : Item(), BaseModel {
    // No need to change this for addons
    override fun registerModels() {
        ValkyrienSkiesMod.proxy.registerItemRender(this, 0)
    }

    init {
        translationKey = name
        this.setRegistryName(name)
        if (hasCreativeTab) {
            creativeTab = ValkyrienSkiesMod.VS_CREATIVE_TAB
        }
        ValkyrienSkiesWorld.ITEMS.add(this)
    }
}