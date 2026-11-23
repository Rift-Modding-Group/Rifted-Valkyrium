package org.valkyrienskies.mixin.pathfinding;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.pathfinding.Path;
import net.minecraft.pathfinding.PathNavigate;
import net.minecraft.pathfinding.PathNavigateGround;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.ships.pathfinding.ShipPathfinder;

@Mixin(PathNavigateGround.class)
public class MixinPathNavigateGround {
    @Unique
    private EntityLiving vs$walkingEntity;

    @Inject(method = "<init>", at = @At("RETURN"))
    private void rememberWalkingEntity(@NotNull EntityLiving entity, @NotNull World world, @NotNull CallbackInfo callback) {
        this.vs$walkingEntity = entity;
    }

    @Inject(method = "getPathToPos", at = @At("HEAD"), cancellable = true)
    private void findShipBlockPath(@NotNull BlockPos target, @NotNull CallbackInfoReturnable<Path> callback) {
        ShipPathfinder.Result result = ShipPathfinder.findPath(
                this.vs$walkingEntity, (PathNavigate) (Object) this,
                new Vec3d(target.getX() + 0.5D, target.getY(), target.getZ() + 0.5D)
        );
        if (result.handled()) callback.setReturnValue(result.path());
    }

    @Inject(method = "getPathToEntityLiving", at = @At("HEAD"), cancellable = true)
    private void findShipEntityPath(@NotNull Entity target, @NotNull CallbackInfoReturnable<Path> callback) {
        ShipPathfinder.Result result = ShipPathfinder.findPath(this.vs$walkingEntity, (PathNavigate) (Object) this, target.getPositionVector());
        if (result.handled()) callback.setReturnValue(result.path());
    }
}
