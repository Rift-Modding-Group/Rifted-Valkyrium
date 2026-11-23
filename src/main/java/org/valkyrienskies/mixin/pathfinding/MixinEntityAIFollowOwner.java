package org.valkyrienskies.mixin.pathfinding;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIFollowOwner;
import net.minecraft.entity.passive.EntityTameable;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.ships.pathfinding.ShipPathfinder;

@Mixin(EntityAIFollowOwner.class)
public class MixinEntityAIFollowOwner {
    @Unique
    private EntityTameable vs$followingPet;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void rememberFollowingPet(@NotNull EntityTameable pet, double speed, float minDistance, float maxDistance, @NotNull CallbackInfo callback) {
        this.vs$followingPet = pet;
    }

    @Inject(method = "updateTask", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;floor(D)I", ordinal = 0), cancellable = true)
    private void teleportFollowingPetToShip(@NotNull CallbackInfo callback) {
        EntityLivingBase owner = this.vs$followingPet.getOwner();
        if (owner != null && ShipPathfinder.teleportToShipNearOwner(this.vs$followingPet, owner)) callback.cancel();
    }
}
