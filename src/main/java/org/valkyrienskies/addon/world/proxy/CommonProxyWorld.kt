package org.valkyrienskies.addon.world.proxy

import net.minecraftforge.common.MinecraftForge
import net.minecraftforge.fml.common.event.FMLStateEvent
import org.valkyrienskies.addon.world.WorldEventsCommon

open class CommonProxyWorld {
    open fun preInit(e: FMLStateEvent) {
        MinecraftForge.EVENT_BUS.register(WorldEventsCommon())
    }
    open fun init(e: FMLStateEvent) {}
    open fun postInit(e: FMLStateEvent) {}
}