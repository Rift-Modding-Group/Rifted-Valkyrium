package org.valkyrienskies.mixin.entity;

import net.minecraft.entity.item.EntityMinecart;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4dc;
import org.joml.Vector3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.JOML;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

@Mixin(EntityMinecart.class)
public class MixinEntityMinecart {
    private ShipTransform transform = null;
    private boolean isInGlobal = true;

    @Inject(
        method = "onUpdate",
        at = @At("HEAD")
    )
    public void preOnUpdate(CallbackInfo ci) {
        if (!VSConfig.minecartsOnShips) return;

        EntityMinecart thisEntityMinecart = (EntityMinecart) ((Object) this);
        if (thisEntityMinecart.world.isRemote) return;
        moveToSubspace();
    }

    @Inject(
        method = "onUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/item/EntityMinecart;doBlockCollisions()V"
        )
    )
    public void preBlockCollisions(CallbackInfo ci) {
        if (!VSConfig.minecartsOnShips) return;

        EntityMinecart thisEntityMinecart = (EntityMinecart) ((Object) this);
        if (thisEntityMinecart.world.isRemote) return;
        moveToGlobal();
    }

    @Inject(
        method = "onUpdate",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/entity/item/EntityMinecart;moveDerailedMinecart()V"
        )
    )
    public void preMoveDerailed(CallbackInfo ci) {
        if (!VSConfig.minecartsOnShips) return;

        EntityMinecart thisEntityMinecart = (EntityMinecart) ((Object) this);
        if (thisEntityMinecart.world.isRemote) return;
        moveToGlobal();
    }

    private void moveToSubspace() {
        EntityMinecart thisEntityMinecart = (EntityMinecart) ((Object) this);
        Vec3d position = thisEntityMinecart.getPositionVector();
        for (PhysicsObject ship : ValkyrienUtils.getPhysosLoadedInWorld(thisEntityMinecart.world)) {
            if (ship.getShipBB().contains(position)) {
                this.transform = ship.getShipTransform();

                transformThis(this.transform.getGlobalToSubspace());
                this.isInGlobal = false;

                return;
            }
        }
    }
    
    private void moveToGlobal() {
        if (!this.isInGlobal) {
            transformThis(transform.getSubspaceToGlobal());
            this.isInGlobal = true;
        }
    }

    private void transformThis(Matrix4dc transform) {
        EntityMinecart thisEntityMinecart = (EntityMinecart) ((Object) this);
        Vector3d pos = transform.transformPosition(JOML.convert(thisEntityMinecart.getPositionVector()));
        Vector3d lastPos = transform.transformPosition(new Vector3d(thisEntityMinecart.lastTickPosX, thisEntityMinecart.lastTickPosY, thisEntityMinecart.lastTickPosZ));

        thisEntityMinecart.setPosition(pos.x, pos.y, pos.z);
        thisEntityMinecart.lastTickPosX = lastPos.x;
        thisEntityMinecart.lastTickPosY = lastPos.y;
        thisEntityMinecart.lastTickPosZ = lastPos.z;
    }
}
