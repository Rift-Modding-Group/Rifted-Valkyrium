package org.valkyrienskies.addon.control.capability.controlNodeUser;

import net.minecraft.nbt.NBTBase;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.EnumFacing;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.Capability.IStorage;
import org.jspecify.annotations.Nullable;

public class StorageControlNodeUser implements IStorage<ICapabilityControlNodeUser> {
    @Override
    public @Nullable NBTTagCompound writeNBT(Capability<ICapabilityControlNodeUser> capability, ICapabilityControlNodeUser object, EnumFacing enumFacing) {
        return new NBTTagCompound();
    }

    @Override
    public void readNBT(Capability<ICapabilityControlNodeUser> capability, ICapabilityControlNodeUser object, EnumFacing enumFacing, NBTBase nBTBase) {

    }
}
