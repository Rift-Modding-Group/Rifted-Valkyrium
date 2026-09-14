package org.valkyrienskies.mixin.client.renderer.entity;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.ShipLocalEntityMovementData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityShipMountData;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.JOML;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

@Mixin(RenderManager.class)
public abstract class MixinRenderManager {
    @Unique
    private boolean hasChanged = false;

    @Shadow
    public abstract void renderEntity(
            Entity entityIn, double x, double y, double z, float yaw,
            float partialTicks, boolean p_188391_10_
    );

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    public void preDoRenderEntity(
            Entity entityIn, double x, double y, double z, float yaw,
            float partialTicks, boolean p_188391_10_, CallbackInfo callbackInfo
    ) {
        if (this.hasChanged) return;

        //---initialize needed local variables---
        PhysicsObject renderedShip = null;
        Vec3d renderedLocalPosition = null;
        //projectiles that stick to a ship are part of the ship visually
        //best use ship local position data to make sure they maintain their
        //yaw when on a ship thats rotating along yaw
        ShipLocalEntityMovementData projectileMovementData = null;

        //well...
        if (entityIn instanceof IProjectile) {
            IEntityShipDraggable draggable = entityIn.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
            projectileMovementData = draggable == null ? null : draggable.getShipLocalMovementData();
            if (projectileMovementData != null && projectileMovementData.isActive() && projectileMovementData.isInitialized()
                    && projectileMovementData.getShipUuid() != null
            ) {
                IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(entityIn.world);
                renderedShip = physObjectWorld == null
                        ? null : physObjectWorld.getPhysObjectFromUUID(projectileMovementData.getShipUuid());
                if (renderedShip != null) {
                    Vector3d relativePosition = projectileMovementData.getRelativePosition(partialTicks);
                    renderedLocalPosition = new Vec3d(relativePosition.x, relativePosition.y, relativePosition.z);
                }
            }
        }
        else {
            EntityShipMountData mountData = ValkyrienUtils.getMountedShipAndPos(entityIn);
            renderedShip = mountData.mountedShip();
            renderedLocalPosition = mountData.mountPos();
        }

        //---apply rendering---
        if (renderedShip != null) {
            double oldPosX = entityIn.posX;
            double oldPosY = entityIn.posY;
            double oldPosZ = entityIn.posZ;

            double oldLastPosX = entityIn.lastTickPosX;
            double oldLastPosY = entityIn.lastTickPosY;
            double oldLastPosZ = entityIn.lastTickPosZ;
            float oldRotationYaw = entityIn.rotationYaw;
            float oldPrevRotationYaw = entityIn.prevRotationYaw;
            float oldRotationPitch = entityIn.rotationPitch;
            float oldPrevRotationPitch = entityIn.prevRotationPitch;

            GL11.glPushMatrix();

            renderedShip.getShipRenderer().applyRenderTransform(partialTicks);

            if (renderedLocalPosition != null) {
                Vector3d localPosition = JOML.convert(renderedLocalPosition);

                localPosition.x -= renderedShip.getShipRenderer().offsetPos.getX();
                localPosition.y -= renderedShip.getShipRenderer().offsetPos.getY();
                localPosition.z -= renderedShip.getShipRenderer().offsetPos.getZ();

                x = entityIn.posX = entityIn.lastTickPosX = localPosition.x;
                y = entityIn.posY = entityIn.lastTickPosY = localPosition.y;
                z = entityIn.posZ = entityIn.lastTickPosZ = localPosition.z;
            }
            //this is to ensure that projectiles that stick maintain
            //their yaw when on a ship thats rotating along yaw
            if (projectileMovementData != null) {
                float localYaw = (float) projectileMovementData.getRelativeYaw(partialTicks);
                float localPitch = (float) projectileMovementData.getPitch(partialTicks);
                entityIn.rotationYaw = localYaw;
                entityIn.prevRotationYaw = localYaw;
                entityIn.rotationPitch = localPitch;
                entityIn.prevRotationPitch = localPitch;
                yaw = localYaw;
            }

            this.hasChanged = true;
            this.renderEntity(entityIn, x, y, z, yaw, partialTicks, p_188391_10_);
            this.hasChanged = false;

            if (renderedLocalPosition != null) {
                renderedShip.getShipRenderer().applyInverseTransform(partialTicks);
            }
            GL11.glPopMatrix();

            entityIn.posX = oldPosX;
            entityIn.posY = oldPosY;
            entityIn.posZ = oldPosZ;

            entityIn.lastTickPosX = oldLastPosX;
            entityIn.lastTickPosY = oldLastPosY;
            entityIn.lastTickPosZ = oldLastPosZ;
            entityIn.rotationYaw = oldRotationYaw;
            entityIn.prevRotationYaw = oldPrevRotationYaw;
            entityIn.rotationPitch = oldRotationPitch;
            entityIn.prevRotationPitch = oldPrevRotationPitch;

            callbackInfo.cancel();
        }
    }
}
