package org.valkyrienskies.addon.world

import net.minecraft.potion.Potion

class VSWorldPotion(isBadEffectIn: Boolean, liquidColorIn: Int, name : String) : Potion(isBadEffectIn, liquidColorIn) {

    init {
        setPotionName("effect.$name")
        setRegistryName(ValkyrienSkiesWorld.MOD_ID + ":" + name)
        setIconIndex(0, 1)
    }
}