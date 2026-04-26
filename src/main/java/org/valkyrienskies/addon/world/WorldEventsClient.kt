package org.valkyrienskies.addon.world

import net.minecraft.client.Minecraft
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.valkyrienskies.addon.world.util.LevitationUtil

class WorldEventsClient {

    @SubscribeEvent
    fun clientTick(event: TickEvent.ClientTickEvent) {
        if (event.phase == TickEvent.Phase.START) {
            if (Minecraft.getMinecraft().world != null && !Minecraft.getMinecraft().isGamePaused)
                LevitationUtil.addEntityLevitationEffects(Minecraft.getMinecraft().world)
        }
    }
}