package org.valkyrienskies.mixin.pathfinding;

import net.minecraft.entity.EntityLiving;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.ships.pathfinding.ShipPath;
import org.valkyrienskies.mod.common.ships.pathfinding.ShipPathfinder;

@Mixin(PathNavigate.class)
public class MixinPathNavigate {
    @Unique
    private EntityLiving vs$navigatingEntity;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void rememberNavigatingEntity(@NotNull EntityLiving entity, @NotNull World world, @NotNull CallbackInfo callback) {
        this.vs$navigatingEntity = entity;
    }

    @Inject(method = "onUpdateNavigation", at = @At("HEAD"), cancellable = true)
    private void followMovingShipPath(@NotNull CallbackInfo callback) {
        PathNavigate navigator = (PathNavigate) (Object) this;
        if (navigator.getPath() instanceof ShipPath path) {
            path.update(navigator, false);
            callback.cancel();
        }
    }

    @Inject(method = "updatePath", at = @At("HEAD"), cancellable = true)
    private void updateMovingShipPath(@NotNull CallbackInfo callback) {
        PathNavigate navigator = (PathNavigate) (Object) this;
        if (navigator.getPath() instanceof ShipPath path) {
            path.update(navigator, true);
            callback.cancel();
        }
    }

    @Inject(method = "removeSunnyPath", at = @At("HEAD"), cancellable = true)
    private void keepSurfaceWaypoints(@NotNull CallbackInfo callback) {
        if (((PathNavigate) (Object) this).getPath() instanceof ShipPath) callback.cancel();
    }

    @Inject(method = "canEntityStandOnPos", at = @At("RETURN"), cancellable = true)
    private void allowShipWanderingTargets(@NotNull BlockPos position, @NotNull CallbackInfoReturnable<Boolean> callback) {
        if (!callback.getReturnValueZ() && ShipPathfinder.canStandOnShip(this.vs$navigatingEntity, (PathNavigate) (Object) this, position)) {
            callback.setReturnValue(true);
        }
    }
}
