package org.valkyrienskies.mixin.entity;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityTrackerEntry;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.anchored_mount.IShipAnchoredMount;

/**
 * fuck rustic chairs
 * this is to make rustic chairs not autodismount the player when ship is going too fast and they sittin for a good while
 * */
@Mixin(EntityTrackerEntry.class)
public class MixinEntityTrackerEntry {
    @Shadow
    @Final
    private Entity trackedEntity;

    @Inject(method = "isVisibleTo", at = @At("HEAD"), cancellable = true)
    private void keepShipAnchoredMountVisibleToPassenger(EntityPlayerMP player, CallbackInfoReturnable<Boolean> callbackInfo) {
        IShipAnchoredMount anchoredMount = this.trackedEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        if (this.isRusticChairEntity(this.trackedEntity) && this.trackedEntity.getPassengers().isEmpty()
            && anchoredMount != null && (anchoredMount.isAnchoredToShip()
            || anchoredMount.tryAnchorMount(this.trackedEntity))
        ) {
            // Rustic kills a client chair that ticks without a passenger and then asks the server
            // to dismount. Wait until startRiding has populated the passenger list before spawning it.
            callbackInfo.setReturnValue(false);
            return;
        }

        if (this.isShipAnchoredPassenger(player)) {
            callbackInfo.setReturnValue(true);
        }
    }

    @Inject(method = "isPlayerWatchingThisChunk", at = @At("HEAD"), cancellable = true)
    private void keepShipAnchoredMountTrackedByPassenger(EntityPlayerMP player, CallbackInfoReturnable<Boolean> callbackInfo) {
        if (this.isShipAnchoredPassenger(player)) {
            callbackInfo.setReturnValue(true);
        }
    }

    private boolean isRusticChairEntity(@NotNull Entity entity) {
        ResourceLocation entityId = EntityList.getKey(entity);
        return entityId != null && "rustic".equals(entityId.getNamespace()) && entityId.getPath().equals("chair");
    }

    private boolean isShipAnchoredPassenger(EntityPlayerMP player) {
        if (!this.trackedEntity.isPassenger(player)) return false;

        IShipAnchoredMount anchoredMount = this.trackedEntity.getCapability(VSCapabilityRegistry.VS_SHIP_ANCHORED_MOUNT, null);
        return anchoredMount != null && (anchoredMount.isAnchoredToShip() || anchoredMount.tryAnchorMount(this.trackedEntity));
    }
}
