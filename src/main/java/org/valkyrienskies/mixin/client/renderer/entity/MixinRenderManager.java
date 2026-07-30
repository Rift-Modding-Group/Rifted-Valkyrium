package org.valkyrienskies.mixin.client.renderer.entity;

import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.valkyrienskies.mod.client.entity_position.EntityRenderPositionManager;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityShipMountData;
import org.valkyrienskies.mod.common.util.JOML;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

@Mixin(RenderManager.class)
public abstract class MixinRenderManager {
    private boolean renderingShipRelativeEntity;

    @Shadow
    public abstract void renderEntity(
            Entity entityIn, double x, double y, double z, float yaw,
            float partialTicks, boolean p_188391_10_
    );

    @Inject(method = "renderEntity", at = @At("HEAD"), cancellable = true)
    private void renderMountedEntity(
            Entity entityIn, double x, double y, double z, float yaw,
            float partialTicks, boolean p_188391_10_, CallbackInfo callbackInfo
    ) {
        if (this.renderingShipRelativeEntity) return;

        EntityShipMountData mountData = ValkyrienUtils.getMountedShipAndPos(entityIn);
        if (mountData.isMounted()) {
            double oldPosX = entityIn.posX;
            double oldPosY = entityIn.posY;
            double oldPosZ = entityIn.posZ;
            double oldLastPosX = entityIn.lastTickPosX;
            double oldLastPosY = entityIn.lastTickPosY;
            double oldLastPosZ = entityIn.lastTickPosZ;

            GL11.glPushMatrix();
            try {
                mountData.mountedShip()
                        .getShipRenderer()
                        .applyRenderTransform(partialTicks);
                Vec3d mountPos = mountData.mountPos();
                if (mountPos != null) {
                    Vector3d localPosition = JOML.convert(mountPos);
                    localPosition.x -= mountData.mountedShip()
                            .getShipRenderer().offsetPos.getX();
                    localPosition.y -= mountData.mountedShip()
                            .getShipRenderer().offsetPos.getY();
                    localPosition.z -= mountData.mountedShip()
                            .getShipRenderer().offsetPos.getZ();

                    x = entityIn.posX = entityIn.lastTickPosX =
                            localPosition.x;
                    y = entityIn.posY = entityIn.lastTickPosY =
                            localPosition.y;
                    z = entityIn.posZ = entityIn.lastTickPosZ =
                            localPosition.z;
                }

                this.renderingShipRelativeEntity = true;
                this.renderEntity(
                        entityIn,
                        x,
                        y,
                        z,
                        yaw,
                        partialTicks,
                        p_188391_10_
                );
            }
            finally {
                this.renderingShipRelativeEntity = false;
                entityIn.posX = oldPosX;
                entityIn.posY = oldPosY;
                entityIn.posZ = oldPosZ;
                entityIn.lastTickPosX = oldLastPosX;
                entityIn.lastTickPosY = oldLastPosY;
                entityIn.lastTickPosZ = oldLastPosZ;
                GL11.glPopMatrix();
            }
        }
        else {
            float carrierYawOffset = EntityRenderPositionManager.getCarrierRenderYawOffset(entityIn, entityIn.world);
            if (Math.abs(carrierYawOffset) < 1.0E-4F) return;

            float oldRotationYaw = entityIn.rotationYaw;
            float oldPrevRotationYaw = entityIn.prevRotationYaw;
            EntityLivingBase living = entityIn instanceof EntityLivingBase ? (EntityLivingBase) entityIn : null;
            float oldRenderYawOffset = 0.0F;
            float oldPrevRenderYawOffset = 0.0F;
            float oldRotationYawHead = 0.0F;
            float oldPrevRotationYawHead = 0.0F;
            if (living != null) {
                oldRenderYawOffset = living.renderYawOffset;
                oldPrevRenderYawOffset = living.prevRenderYawOffset;
                oldRotationYawHead = living.rotationYawHead;
                oldPrevRotationYawHead = living.prevRotationYawHead;
            }

            try {
                entityIn.rotationYaw += carrierYawOffset;
                entityIn.prevRotationYaw += carrierYawOffset;
                if (living != null) {
                    living.renderYawOffset += carrierYawOffset;
                    living.prevRenderYawOffset += carrierYawOffset;
                    living.rotationYawHead += carrierYawOffset;
                    living.prevRotationYawHead += carrierYawOffset;
                }

                this.renderingShipRelativeEntity = true;
                this.renderEntity(
                        entityIn, x, y, z, yaw + carrierYawOffset,
                        partialTicks, p_188391_10_
                );
            }
            finally {
                this.renderingShipRelativeEntity = false;
                entityIn.rotationYaw = oldRotationYaw;
                entityIn.prevRotationYaw = oldPrevRotationYaw;
                if (living != null) {
                    living.renderYawOffset = oldRenderYawOffset;
                    living.prevRenderYawOffset = oldPrevRenderYawOffset;
                    living.rotationYawHead = oldRotationYawHead;
                    living.prevRotationYawHead = oldPrevRotationYawHead;
                }
            }
        }
        callbackInfo.cancel();
    }
}
