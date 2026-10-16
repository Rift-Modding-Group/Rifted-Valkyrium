package org.valkyrienskies.mixin.entity;

import net.minecraft.block.BlockRailBase;
import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

/**
 * Runs vanilla minecart rail movement in a ship's local block coordinates while keeping the
 * entity itself in world coordinates outside that part of the tick.
 */
@Mixin(EntityMinecart.class)
public class MixinEntityMinecart {
    @Unique
    @Nullable
    private PhysicsObject valkyrienskies$railShip;

    @Unique
    private boolean valkyrienskies$inShipCoordinates;

    @Unique
    private boolean valkyrienskies$followedShipRail;

    @Inject(method = "onUpdate", at = @At("HEAD"))
    private void moveToShipRailCoordinates(CallbackInfo callbackInfo) {
        EntityMinecart minecart = (EntityMinecart) (Object) this;
        if (!VSConfig.minecartsOnShips || minecart.world.isRemote) return;

        this.valkyrienskies$railShip = null;
        this.valkyrienskies$inShipCoordinates = false;
        this.valkyrienskies$followedShipRail = false;
        PhysicsObject railShip = this.valkyrienskies$findRailShip(minecart);
        if (railShip == null) return;

        this.valkyrienskies$railShip = railShip;
        this.valkyrienskies$transformPositionAndMotion(
                minecart, railShip.getShipTransform(),
                TransformType.GLOBAL_TO_SUBSPACE
        );
        this.valkyrienskies$inShipCoordinates = true;
    }

    @Inject(
            method = "onUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/item/EntityMinecart;moveDerailedMinecart()V"
            )
    )
    private void leaveShipCoordinatesBeforeDerailedMovement(CallbackInfo callbackInfo) {
        this.valkyrienskies$moveToWorldCoordinates(false);
    }

    @Inject(
            method = "onUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/item/EntityMinecart;doBlockCollisions()V",
                    shift = At.Shift.AFTER
            )
    )
    private void leaveShipCoordinatesAfterRailBlockCollisions(CallbackInfo callbackInfo) {
        this.valkyrienskies$moveToWorldCoordinates(true);
    }

    @Inject(method = "onUpdate", at = @At("RETURN"))
    private void ensureWorldCoordinatesAfterUpdate(CallbackInfo callbackInfo) {
        this.valkyrienskies$moveToWorldCoordinates(false);
        this.valkyrienskies$railShip = null;
        this.valkyrienskies$followedShipRail = false;
    }

    @Redirect(
            method = "onUpdate",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/entity/item/EntityMinecart;getCurrentRailPosition()Lnet/minecraft/util/math/BlockPos;"
            )
    )
    private BlockPos useShipRailPositionForUpdateEvent(EntityMinecart minecart) {
        Vector3d railPosition = new Vector3d(minecart.posX, minecart.posY, minecart.posZ);
        if (this.valkyrienskies$followedShipRail && this.valkyrienskies$railShip != null) {
            this.valkyrienskies$railShip.getShipTransform().transformPosition(railPosition, TransformType.GLOBAL_TO_SUBSPACE);
        }
        return this.valkyrienskies$getRailPosition(minecart, railPosition);
    }

    @Unique
    @Nullable
    private PhysicsObject valkyrienskies$findRailShip(@NotNull EntityMinecart minecart) {
        Vector3d worldPosition = new Vector3d(minecart.posX, minecart.posY, minecart.posZ);
        for (PhysicsObject ship : ValkyrienUtils.getPhysosLoadedInWorld(minecart.world)) {
            if (!ship.getShipBB().grow(1D).contains(minecart.getPositionVector())) continue;

            Vector3d localPosition = new Vector3d(worldPosition);
            ship.getShipTransform().transformPosition(localPosition, TransformType.GLOBAL_TO_SUBSPACE);
            if (this.valkyrienskies$isRailAtCart(minecart, localPosition)) return ship;
        }
        return null;
    }

    @Unique
    private boolean valkyrienskies$isRailAtCart(@NotNull EntityMinecart minecart, @NotNull Vector3d localPosition) {
        BlockPos railPosition = this.valkyrienskies$getRailPosition(minecart, localPosition);
        return BlockRailBase.isRailBlock(minecart.world, railPosition);
    }

    @Unique
    @NotNull
    private BlockPos valkyrienskies$getRailPosition(@NotNull EntityMinecart minecart, @NotNull Vector3d position) {
        int x = MathHelper.floor(position.x);
        int y = MathHelper.floor(position.y);
        int z = MathHelper.floor(position.z);
        BlockPos railPosition = new BlockPos(x, y, z);
        if (BlockRailBase.isRailBlock(minecart.world, railPosition.down())) return railPosition.down();
        return railPosition;
    }

    @Unique
    private void valkyrienskies$moveToWorldCoordinates(boolean followedRail) {
        if (!this.valkyrienskies$inShipCoordinates || this.valkyrienskies$railShip == null) return;

        EntityMinecart minecart = (EntityMinecart) (Object) this;
        PhysicsObject railShip = this.valkyrienskies$railShip;
        this.valkyrienskies$transformPositionAndMotion(minecart, railShip.getShipTransform(), TransformType.SUBSPACE_TO_GLOBAL);
        this.valkyrienskies$inShipCoordinates = false;

        if (followedRail) {
            this.valkyrienskies$followedShipRail = true;
            EntityDraggable.startTrackingShip(minecart, railShip.getShipData());
        }
    }

    @Unique
    private void valkyrienskies$transformPositionAndMotion(
            @NotNull EntityMinecart minecart,
            @NotNull ShipTransform transform,
            @NotNull TransformType transformType
    ) {
        Vector3d position = new Vector3d(minecart.posX, minecart.posY, minecart.posZ);
        Vector3d previousPosition = new Vector3d(minecart.prevPosX, minecart.prevPosY, minecart.prevPosZ);
        Vector3d motion = new Vector3d(minecart.motionX, minecart.motionY, minecart.motionZ);
        transform.transformPosition(position, transformType);
        transform.transformPosition(previousPosition, transformType);
        transform.transformDirection(motion, transformType);

        minecart.setPosition(position.x, position.y, position.z);
        minecart.prevPosX = previousPosition.x;
        minecart.prevPosY = previousPosition.y;
        minecart.prevPosZ = previousPosition.z;
        minecart.motionX = motion.x;
        minecart.motionY = motion.y;
        minecart.motionZ = motion.z;
    }
}
