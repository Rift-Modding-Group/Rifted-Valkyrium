package org.valkyrienskies.addon.control.capability.controlNodeUser;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;

public class ControlNodeUserCapabilityProvider implements ICapabilitySerializable<NBTTagCompound> {
    private ICapabilityControlNodeUser inst = ValkyrienSkiesControl.controlNodeUserCapability.getDefaultInstance();

    @Override
    public boolean hasCapability(@NonNull Capability<?> capability, @Nullable EnumFacing enumFacing) {
        return capability == ValkyrienSkiesControl.controlNodeUserCapability;
    }

    @Override
    public @Nullable <T> T getCapability(@NonNull Capability<T> capability, @Nullable EnumFacing enumFacing) {
        return capability == ValkyrienSkiesControl.controlNodeUserCapability ?
                ValkyrienSkiesControl.controlNodeUserCapability.cast(inst) : null;
    }

    //-----shouldnt matter that much i hope-----
    @Override
    public NBTTagCompound serializeNBT() {
        return new NBTTagCompound();
    }

    @Override
    public void deserializeNBT(NBTTagCompound nBTBase) {}
}
