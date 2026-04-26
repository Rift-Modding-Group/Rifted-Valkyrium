package org.valkyrienskies.addon.world

import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.util.ResourceLocation
import net.minecraftforge.event.AttachCapabilitiesEvent
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.WorldTickEvent
import org.valkyrienskies.addon.world.block.BlockValkyriumOre
import org.valkyrienskies.addon.world.capability.AntiGravityCapabilityProvider
import org.valkyrienskies.addon.world.config.VSWorldConfig
import org.valkyrienskies.addon.world.util.LevitationUtil
import org.valkyrienskies.mod.common.config.VSConfig

class WorldEventsCommon {

    @SubscribeEvent
    fun onAttachCapabilityEventItem(event: AttachCapabilitiesEvent<ItemStack>) {
        val stack = event.getObject() as ItemStack
        val item = stack.getItem()
        if (item is ItemValkyriumCrystal) {
            event.addCapability(
                    ResourceLocation(ValkyrienSkiesWorld.MOD_ID, "levitation_strength_capability"),
                    AntiGravityCapabilityProvider(1.0))
        }
        if (stack.getItem() is ItemBlock) {
            val blockItem = stack.getItem() as ItemBlock
            if (blockItem.block is BlockValkyriumOre) {
                event.addCapability(
                        ResourceLocation(ValkyrienSkiesWorld.MOD_ID, "levitation_strength_capability"),
                        AntiGravityCapabilityProvider(1.0))
            }
        }
    }

    @SubscribeEvent
    fun worldTick(event: WorldTickEvent) {
        if (event.phase == TickEvent.Phase.START) {
            LevitationUtil.addEntityLevitationEffects(event.world);
        }
    }

}