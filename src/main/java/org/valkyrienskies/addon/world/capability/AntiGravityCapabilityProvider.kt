package org.valkyrienskies.addon.world.capability

import net.minecraft.nbt.NBTTagDouble
import net.minecraft.util.EnumFacing
import net.minecraftforge.common.capabilities.Capability
import net.minecraftforge.common.capabilities.ICapabilitySerializable
import org.valkyrienskies.addon.world.ValkyrienSkiesWorld

class AntiGravityCapabilityProvider(multiplier: Double) : ICapabilitySerializable<NBTTagDouble> {
    private val inst: ICapabilityAntiGravity = ValkyrienSkiesWorld.ANTI_GRAVITY_CAPABILITY.defaultInstance!!
    override fun hasCapability(capability: Capability<*>, facing: EnumFacing?): Boolean {
        return capability === ValkyrienSkiesWorld.ANTI_GRAVITY_CAPABILITY
    }

    override fun <T> getCapability(capability: Capability<T>, facing: EnumFacing?): T? {
        return if (capability === ValkyrienSkiesWorld.ANTI_GRAVITY_CAPABILITY)
            ValkyrienSkiesWorld.ANTI_GRAVITY_CAPABILITY.cast(inst) else null
    }

    override fun serializeNBT(): NBTTagDouble {
        return ValkyrienSkiesWorld.ANTI_GRAVITY_CAPABILITY.storage
                .writeNBT(ValkyrienSkiesWorld.ANTI_GRAVITY_CAPABILITY, inst, null) as NBTTagDouble
    }

    override fun deserializeNBT(nbt: NBTTagDouble) {
        ValkyrienSkiesWorld.ANTI_GRAVITY_CAPABILITY.storage
                .readNBT(ValkyrienSkiesWorld.ANTI_GRAVITY_CAPABILITY, inst, null, nbt)
    }

    init {
        inst.multiplier = multiplier
    }
}