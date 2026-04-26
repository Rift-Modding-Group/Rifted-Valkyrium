package org.valkyrienskies.addon.world.capability

import net.minecraft.nbt.NBTBase
import net.minecraft.nbt.NBTTagDouble
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.Capability.IStorage

class StorageAntiGravity : IStorage<ICapabilityAntiGravity> {
    override fun writeNBT(capability: Capability<ICapabilityAntiGravity>,
                          instance: ICapabilityAntiGravity,
                          side: EnumFacing?): NBTBase? {
        return NBTTagDouble(instance.multiplier)
    }

    override fun readNBT(capability: Capability<ICapabilityAntiGravity>,
                         instance: ICapabilityAntiGravity, side: EnumFacing?,
                         nbt: NBTBase) {
        val tagDouble = nbt as NBTTagDouble
        instance.multiplier = tagDouble.double
    }
}