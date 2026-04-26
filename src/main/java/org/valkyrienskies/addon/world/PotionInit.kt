package org.valkyrienskies.addon.world

import net.minecraft.init.Items
import net.minecraft.init.PotionTypes
import net.minecraft.potion.Potion
import net.minecraft.potion.PotionEffect
import net.minecraft.potion.PotionHelper
import net.minecraft.potion.PotionType
import net.minecraftforge.event.RegistryEvent

class PotionInit {

    companion object {
        private const val LEVITATION_POTION_NAME = "valkyrium-levitation"
        private const val LEVITATION_JUMP_POTION_NAME = "valkyrium-levitation-jump"

        val LEVITATION_POTION_EFFECT = VSWorldPotion(false, 8360, LEVITATION_POTION_NAME)
        // A potion that has 1 second of very strong levitation, followed by 9 seconds of weak levitation
        val LEVITATION_JUMP_POTION_EFFECT = VSWorldPotion(false, 11600000, LEVITATION_JUMP_POTION_NAME)

        private val LEVITATION_POTION_TYPE = PotionType(LEVITATION_POTION_NAME, PotionEffect(LEVITATION_POTION_EFFECT, 1200, 0)).setRegistryName(LEVITATION_POTION_NAME)
        private val LONG_LEVITATION_POTION_TYPE = PotionType(LEVITATION_POTION_NAME, PotionEffect(LEVITATION_POTION_EFFECT, 2400, 0)).setRegistryName("long$LEVITATION_POTION_NAME")
        private val STRONG_LEVITATION_POTION_TYPE = PotionType(LEVITATION_POTION_NAME, PotionEffect(LEVITATION_POTION_EFFECT, 120, 1)).setRegistryName("strong$LEVITATION_POTION_NAME")

        private val LEVITATION_JUMP_POTION_TYPE = PotionType(LEVITATION_JUMP_POTION_NAME, PotionEffect(LEVITATION_JUMP_POTION_EFFECT, 120, 0)).setRegistryName(LEVITATION_JUMP_POTION_NAME)

        fun registerPotions(event: RegistryEvent.Register<Potion?>) {
            event.registry.register(LEVITATION_POTION_EFFECT)
            event.registry.register(LEVITATION_JUMP_POTION_EFFECT)
        }

        fun registerPotionTypes(event: RegistryEvent.Register<PotionType?>) {
            event.registry.register(LEVITATION_POTION_TYPE)
            event.registry.register(LONG_LEVITATION_POTION_TYPE)
            event.registry.register(STRONG_LEVITATION_POTION_TYPE)

            event.registry.register(LEVITATION_JUMP_POTION_TYPE)

            registerPotionMixes()
        }

        private fun registerPotionMixes() {
            PotionHelper.addMix(PotionTypes.AWKWARD, ValkyrienSkiesWorld.INSTANCE.valkyriumCrystal, LEVITATION_POTION_TYPE)
            PotionHelper.addMix(LEVITATION_POTION_TYPE, Items.REDSTONE, LONG_LEVITATION_POTION_TYPE)
            PotionHelper.addMix(LEVITATION_POTION_TYPE, ValkyrienSkiesWorld.INSTANCE.valkyriumCrystal, STRONG_LEVITATION_POTION_TYPE)
            PotionHelper.addMix(LONG_LEVITATION_POTION_TYPE, ValkyrienSkiesWorld.INSTANCE.valkyriumCrystal, STRONG_LEVITATION_POTION_TYPE)
            PotionHelper.addMix(STRONG_LEVITATION_POTION_TYPE, Items.COAL, LEVITATION_JUMP_POTION_TYPE)
        }
    }
}