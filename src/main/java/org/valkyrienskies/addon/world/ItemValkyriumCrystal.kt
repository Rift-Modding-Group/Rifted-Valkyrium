package org.valkyrienskies.addon.world

import net.minecraft.client.resources.I18n
import net.minecraft.client.util.ITooltipFlag
import net.minecraft.item.ItemStack
import net.minecraft.util.text.TextFormatting
import net.minecraft.world.World
import org.valkyrienskies.addon.world.util.BaseItem

class ItemValkyriumCrystal : BaseItem("valkyrium_crystal", true) {
    override fun addInformation(stack: ItemStack, player: World?,
                                itemInformation: MutableList<String>, advanced: ITooltipFlag) {

        itemInformation.add(TextFormatting.ITALIC.toString() + "" + TextFormatting.BLUE +
                TextFormatting.ITALIC + I18n.format("tooltip.vs_world.valkyrium_crystal"))
    }
}