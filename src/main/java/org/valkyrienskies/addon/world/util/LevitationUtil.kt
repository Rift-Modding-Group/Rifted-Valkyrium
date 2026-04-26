package org.valkyrienskies.addon.world.util

import net.minecraft.entity.EntityLivingBase
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.item.ItemBlock
import net.minecraft.world.World
import org.valkyrienskies.addon.world.ItemValkyriumCrystal
import org.valkyrienskies.addon.world.PotionInit
import org.valkyrienskies.addon.world.ValkyrienSkiesWorld
import org.valkyrienskies.addon.world.block.BlockValkyriumOre
import org.valkyrienskies.addon.world.config.VSWorldConfig

class LevitationUtil {

    companion object {

        /**
         * Adds levitation effects to all entities in world
         */
        fun addEntityLevitationEffects(world : World) {
            for (entity in world.loadedEntityList) {
                if (entity is EntityItem) {
                    val itemStack = entity.item
                    val capability = itemStack.getCapability(ValkyrienSkiesWorld.ANTI_GRAVITY_CAPABILITY, null)
                    if (capability != null) {
                        entity.addVelocity(0.0, .08 * capability.multiplier, 0.0)
                    }
                } else if (entity is EntityLivingBase) {
                    if (entity is EntityPlayer) {
                        // Add levitation based on player inventory
                        if (VSWorldConfig.valkyriumItemsLiftPlayers && !entity.isCreative) {
                            var addedUpVelocity = 0.0
                            for (stackArray in entity.inventory.allInventories) {
                                for (stack in stackArray) {
                                    if (stack != null) {
                                        if (stack.getItem() is ItemBlock) {
                                            val blockItem = stack.getItem() as ItemBlock
                                            if (blockItem.block is BlockValkyriumOre) {
                                                addedUpVelocity += .00025 * stack.stackSize * VSWorldConfig.valkyriumOreForce
                                            }
                                        } else if (stack.getItem() is ItemValkyriumCrystal) {
                                            addedUpVelocity += .00025 * stack.stackSize * VSWorldConfig.valkyriumCrystalForce
                                        }
                                    }
                                }
                            }
                            if (addedUpVelocity > 0) {
                                entity.addVelocity(0.0, addedUpVelocity, 0.0)
                                // Reset fall damage if its big enough
                                if (addedUpVelocity > .05) {
                                    entity.fallDistance = 0F
                                }
                            }
                        }
                    }
                    // Add levitation based on potion effects
                    if (entity.isPotionActive(PotionInit.LEVITATION_POTION_EFFECT)) {
                        val levitationEffect = entity.getActivePotionEffect(PotionInit.LEVITATION_POTION_EFFECT)

                        if (entity is EntityPlayer && (entity.isCreative || entity.isSpectator)) break

                        when (levitationEffect!!.amplifier) {
                            0 -> entity.addVelocity(0.0, .07, 0.0)
                            1 -> entity.addVelocity(0.0, .1, 0.0)
                        }

                        // Reset fall damage
                        entity.fallDistance = 0F
                    }

                    if (entity.isPotionActive(PotionInit.LEVITATION_JUMP_POTION_EFFECT)) {
                        val levitationEffect = entity.getActivePotionEffect(PotionInit.LEVITATION_JUMP_POTION_EFFECT)

                        if (entity is EntityPlayer && (entity.isCreative || entity.isSpectator)) break

                        if (levitationEffect!!.duration > 120 * .9) {
                            entity.addVelocity(0.0, .2, 0.0);
                        } else {
                            entity.addVelocity(0.0, .05, 0.0);
                        }

                        // Reset fall damage
                        entity.fallDistance = 0F
                    }
                }
            }
        }
    }
}