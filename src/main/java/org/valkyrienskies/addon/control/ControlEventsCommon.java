package org.valkyrienskies.addon.control;

import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import org.valkyrienskies.addon.control.capability.controlNodeUser.ControlNodeUserCapabilityProvider;
import org.valkyrienskies.addon.control.capability.lastRelay.LastRelayCapabilityProvider;
import org.valkyrienskies.addon.control.item.ItemBaseWire;

public class ControlEventsCommon {
    @SubscribeEvent
    public void onAttachCapabilityEventItem(AttachCapabilitiesEvent<ItemStack> event) {
        if (event.getObject().getItem() instanceof ItemBaseWire) {
            event.addCapability(new ResourceLocation(ValkyrienSkiesControl.MOD_ID, "LastRelay"),
                new LastRelayCapabilityProvider());
        }
    }

    @SubscribeEvent
    public void onAttachCapabilityEventPlayer(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof EntityPlayer) {
            event.addCapability(new ResourceLocation(ValkyrienSkiesControl.MOD_ID, "ControlNodeUser"), new ControlNodeUserCapabilityProvider());
        }
    }

}
