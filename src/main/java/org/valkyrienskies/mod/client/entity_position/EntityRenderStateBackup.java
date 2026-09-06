package org.valkyrienskies.mod.client.entity_position;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;

/**
 * Captures an entity's persistent tick state and applies a precomputed state for one render pass.
 */
public class EntityRenderStateBackup {
    @NotNull
    private final Entity entity;
    @Nullable
    private final EntityLivingBase livingEntity;
    @NotNull
    private final AxisAlignedBB boundingBox;
    @NotNull
    private final Vector3d renderPosition;
    private final double posX;
    private final double posY;
    private final double posZ;
    private final double prevPosX;
    private final double prevPosY;
    private final double prevPosZ;
    private final double lastTickPosX;
    private final double lastTickPosY;
    private final double lastTickPosZ;
    private final float rotationYaw;
    private final float rotationPitch;
    private final float prevRotationYaw;
    private final float prevRotationPitch;
    private float renderYawOffset;
    private float prevRenderYawOffset;
    private float rotationYawHead;
    private float prevRotationYawHead;
    private boolean hasShipLocalRotation;
    private float shipLocalRenderYaw;
    private float shipLocalRenderPitch;
    private float shipLocalRenderHeadYaw;
    private final float partialTicks;

    public EntityRenderStateBackup(@NotNull Entity entity, @NotNull Vector3dc renderPosition, double partialTicks) {
        this.entity = entity;
        this.livingEntity = entity instanceof EntityLivingBase entityLivingBase ? entityLivingBase : null;
        this.boundingBox = entity.getEntityBoundingBox();
        this.renderPosition = new Vector3d(renderPosition);
        this.partialTicks = (float) Math.clamp(partialTicks, 0D, 1D);
        this.posX = entity.posX;
        this.posY = entity.posY;
        this.posZ = entity.posZ;
        this.prevPosX = entity.prevPosX;
        this.prevPosY = entity.prevPosY;
        this.prevPosZ = entity.prevPosZ;
        this.lastTickPosX = entity.lastTickPosX;
        this.lastTickPosY = entity.lastTickPosY;
        this.lastTickPosZ = entity.lastTickPosZ;
        this.rotationYaw = entity.rotationYaw;
        this.rotationPitch = entity.rotationPitch;
        this.prevRotationYaw = entity.prevRotationYaw;
        this.prevRotationPitch = entity.prevRotationPitch;

        if (this.livingEntity != null) {
            this.renderYawOffset = this.livingEntity.renderYawOffset;
            this.prevRenderYawOffset = this.livingEntity.prevRenderYawOffset;
            this.rotationYawHead = this.livingEntity.rotationYawHead;
            this.prevRotationYawHead = this.livingEntity.prevRotationYawHead;
        }
    }

    public void setShipLocalRotation(double renderYaw, double renderPitch, double renderHeadYaw) {
        this.hasShipLocalRotation = true;
        this.shipLocalRenderYaw = (float) renderYaw;
        this.shipLocalRenderPitch = (float) renderPitch;
        this.shipLocalRenderHeadYaw = (float) renderHeadYaw;
    }

    public void apply() {
        double offsetX = this.renderPosition.x - this.posX;
        double offsetY = this.renderPosition.y - this.posY;
        double offsetZ = this.renderPosition.z - this.posZ;

        this.entity.posX = this.renderPosition.x;
        this.entity.posY = this.renderPosition.y;
        this.entity.posZ = this.renderPosition.z;
        this.entity.prevPosX = this.renderPosition.x;
        this.entity.prevPosY = this.renderPosition.y;
        this.entity.prevPosZ = this.renderPosition.z;
        this.entity.lastTickPosX = this.renderPosition.x;
        this.entity.lastTickPosY = this.renderPosition.y;
        this.entity.lastTickPosZ = this.renderPosition.z;
        this.entity.setEntityBoundingBox(this.boundingBox.offset(offsetX, offsetY, offsetZ));

        float interpolatedFacingYaw = this.interpolateRotation(this.prevRotationYaw, this.rotationYaw);
        float interpolatedPitch = this.prevRotationPitch + (this.rotationPitch - this.prevRotationPitch) * this.partialTicks;
        float renderYaw = this.hasShipLocalRotation ? this.shipLocalRenderYaw : interpolatedFacingYaw;
        float renderPitch = this.hasShipLocalRotation ? this.shipLocalRenderPitch : interpolatedPitch;
        this.entity.prevRotationYaw = renderYaw;
        this.entity.rotationYaw = renderYaw;
        this.entity.prevRotationPitch = renderPitch;
        this.entity.rotationPitch = renderPitch;

        if (this.livingEntity != null) {
            float interpolatedBodyYaw = this.interpolateRotation(this.prevRenderYawOffset, this.renderYawOffset);
            float interpolatedHeadYaw = this.interpolateRotation(this.prevRotationYawHead, this.rotationYawHead);
            float renderBodyYaw = interpolatedBodyYaw;
            float renderHeadYaw = interpolatedHeadYaw;
            if (this.hasShipLocalRotation) {
                float bodyToFacingOffset = MathHelper.wrapDegrees(interpolatedBodyYaw - interpolatedFacingYaw);
                renderBodyYaw = this.shipLocalRenderYaw + bodyToFacingOffset;
                renderHeadYaw = this.shipLocalRenderHeadYaw;
            }

            this.livingEntity.prevRenderYawOffset = renderBodyYaw;
            this.livingEntity.renderYawOffset = renderBodyYaw;
            this.livingEntity.prevRotationYawHead = renderHeadYaw;
            this.livingEntity.rotationYawHead = renderHeadYaw;
        }
    }

    public void snapPositionToCurrent() {
        this.entity.prevPosX = this.entity.posX;
        this.entity.prevPosY = this.entity.posY;
        this.entity.prevPosZ = this.entity.posZ;
        this.entity.lastTickPosX = this.entity.posX;
        this.entity.lastTickPosY = this.entity.posY;
        this.entity.lastTickPosZ = this.entity.posZ;
    }

    public boolean hasShipLocalRotation() {
        return this.hasShipLocalRotation;
    }

    public float getRenderYawAdjustment() {
        if (!this.hasShipLocalRotation) return 0f;

        float interpolatedFacingYaw = this.interpolateRotation(this.prevRotationYaw, this.rotationYaw);
        return MathHelper.wrapDegrees(this.shipLocalRenderYaw - interpolatedFacingYaw);
    }

    /**
     * Shifts a rider's interpolated orientation by its carrier's render-time yaw correction.
     */
    public void applyYawAdjustment(float yawAdjustment) {
        float adjustedYaw = this.interpolateRotation(this.prevRotationYaw, this.rotationYaw) + yawAdjustment;
        float interpolatedPitch = this.prevRotationPitch + (this.rotationPitch - this.prevRotationPitch) * this.partialTicks;
        this.entity.rotationYaw = adjustedYaw;
        this.entity.prevRotationYaw = adjustedYaw;
        this.entity.rotationPitch = interpolatedPitch;
        this.entity.prevRotationPitch = interpolatedPitch;

        if (this.livingEntity != null) {
            float adjustedBodyYaw = this.interpolateRotation(this.prevRenderYawOffset, this.renderYawOffset) + yawAdjustment;
            float adjustedHeadYaw = this.interpolateRotation(this.prevRotationYawHead, this.rotationYawHead) + yawAdjustment;
            this.livingEntity.renderYawOffset = adjustedBodyYaw;
            this.livingEntity.prevRenderYawOffset = adjustedBodyYaw;
            this.livingEntity.rotationYawHead = adjustedHeadYaw;
            this.livingEntity.prevRotationYawHead = adjustedHeadYaw;
        }
    }

    private float interpolateRotation(float previousRotation, float currentRotation) {
        float rotationDifference = MathHelper.wrapDegrees(currentRotation - previousRotation);
        return previousRotation + rotationDifference * this.partialTicks;
    }

    public void restore() {
        this.entity.posX = this.posX;
        this.entity.posY = this.posY;
        this.entity.posZ = this.posZ;
        this.entity.prevPosX = this.prevPosX;
        this.entity.prevPosY = this.prevPosY;
        this.entity.prevPosZ = this.prevPosZ;
        this.entity.lastTickPosX = this.lastTickPosX;
        this.entity.lastTickPosY = this.lastTickPosY;
        this.entity.lastTickPosZ = this.lastTickPosZ;
        this.entity.rotationYaw = this.rotationYaw;
        this.entity.rotationPitch = this.rotationPitch;
        this.entity.prevRotationYaw = this.prevRotationYaw;
        this.entity.prevRotationPitch = this.prevRotationPitch;
        this.entity.setEntityBoundingBox(this.boundingBox);

        if (this.livingEntity != null) {
            this.livingEntity.renderYawOffset = this.renderYawOffset;
            this.livingEntity.prevRenderYawOffset = this.prevRenderYawOffset;
            this.livingEntity.rotationYawHead = this.rotationYawHead;
            this.livingEntity.prevRotationYawHead = this.prevRotationYawHead;
        }
    }
}
