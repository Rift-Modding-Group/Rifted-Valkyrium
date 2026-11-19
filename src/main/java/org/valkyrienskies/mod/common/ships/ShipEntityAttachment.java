package org.valkyrienskies.mod.common.ships;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import valkyrienwarfare.api.TransformType;
import valkyrienwarfare.api.ShipEntityDeathBehavior;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;

import java.util.Objects;
import java.util.UUID;

/**
 * Pins the initial bottom center of a platform to an entity-local offset while preserving its initial rotation.
 */
public record ShipEntityAttachment(
        @JsonProperty("entityUuid") @NotNull UUID entityUuid,
        @JsonProperty("offsetX") double offsetX,
        @JsonProperty("offsetY") double offsetY,
        @JsonProperty("offsetZ") double offsetZ,
        @JsonProperty("initialYaw") double initialYaw,
        @JsonProperty("rotationX") double rotationX,
        @JsonProperty("rotationY") double rotationY,
        @JsonProperty("rotationZ") double rotationZ,
        @JsonProperty("rotationW") double rotationW,
        @JsonProperty("entityDeathBehavior") @NotNull ShipEntityDeathBehavior entityDeathBehavior
) {
    public ShipEntityAttachment {
        Objects.requireNonNull(entityUuid, "entityUuid");
        Objects.requireNonNull(entityDeathBehavior, "entityDeathBehavior");
        if (!Double.isFinite(offsetX) || !Double.isFinite(offsetY) || !Double.isFinite(offsetZ)
                || !Double.isFinite(initialYaw) || !Double.isFinite(rotationX) || !Double.isFinite(rotationY)
                || !Double.isFinite(rotationZ) || !Double.isFinite(rotationW)) {
            throw new IllegalArgumentException("Entity attachment coordinates and yaw must be finite");
        }
        if (rotationX == 0D && rotationY == 0D && rotationZ == 0D && rotationW == 0D) rotationW = 1D;
    }

    public ShipEntityAttachment(UUID entityUuid, double offsetX, double offsetY, double offsetZ, double initialYaw) {
        this(entityUuid, offsetX, offsetY, offsetZ, initialYaw, ShipEntityDeathBehavior.DETACH);
    }

    public ShipEntityAttachment(UUID entityUuid, double offsetX, double offsetY, double offsetZ, double initialYaw,
                                @NotNull ShipEntityDeathBehavior entityDeathBehavior) {
        this(entityUuid, offsetX, offsetY, offsetZ, initialYaw, 0D, 0D, 0D, 1D, entityDeathBehavior);
    }

    public ShipEntityAttachment(UUID entityUuid, double offsetX, double offsetY, double offsetZ, double initialYaw,
                                double rotationX, double rotationY, double rotationZ, double rotationW) {
        this(entityUuid, offsetX, offsetY, offsetZ, initialYaw, rotationX, rotationY, rotationZ, rotationW, ShipEntityDeathBehavior.DETACH);
    }

    @JsonCreator
    @NotNull
    public static ShipEntityAttachment fromJson(
            @JsonProperty("entityUuid") @NotNull UUID entityUuid,
            @JsonProperty("offsetX") double offsetX,
            @JsonProperty("offsetY") double offsetY,
            @JsonProperty("offsetZ") double offsetZ,
            @JsonProperty("initialYaw") double initialYaw,
            @JsonProperty("rotationX") double rotationX,
            @JsonProperty("rotationY") double rotationY,
            @JsonProperty("rotationZ") double rotationZ,
            @JsonProperty("rotationW") double rotationW,
            @JsonProperty("entityDeathBehavior") @Nullable ShipEntityDeathBehavior entityDeathBehavior,
            @JsonProperty("breakShipBlocksOnEntityDeath") boolean breakShipBlocksOnEntityDeath
    ) {
        if (entityDeathBehavior == null) {
            entityDeathBehavior = breakShipBlocksOnEntityDeath ? ShipEntityDeathBehavior.BREAK_BLOCKS : ShipEntityDeathBehavior.DETACH;
        }
        return new ShipEntityAttachment(entityUuid, offsetX, offsetY, offsetZ, initialYaw,
                rotationX, rotationY, rotationZ, rotationW, entityDeathBehavior);
    }

    @NotNull
    public static ShipEntityAttachment create(@NotNull Entity entity, @NotNull Vec3d offset, @NotNull ShipTransform transform) {
        return create(entity, offset, transform, ShipEntityDeathBehavior.DETACH);
    }

    @NotNull
    public static ShipEntityAttachment create(@NotNull Entity entity, @NotNull Vec3d offset, @NotNull ShipTransform transform,
                                              @NotNull ShipEntityDeathBehavior entityDeathBehavior) {
        Quaterniond rotation = transform.rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL);
        return new ShipEntityAttachment(entity.getUniqueID(), offset.x, offset.y, offset.z, getEntityYaw(entity),
                rotation.x, rotation.y, rotation.z, rotation.w, entityDeathBehavior);
    }

    public static double getEntityYaw(@NotNull Entity entity) {
        if (entity instanceof EntityLivingBase livingEntity) return livingEntity.renderYawOffset;
        return entity.rotationYaw;
    }

    @NotNull
    public ShipTransform createTransform(@NotNull Entity entity, @NotNull Vector3dc attachmentPoint, @NotNull Vector3dc centerOfMass) {
        return this.createTransform(entity, 1D, attachmentPoint, centerOfMass);
    }

    @NotNull
    public ShipTransform createTransform(@NotNull Entity entity, double partialTick,
                                         @NotNull Vector3dc attachmentPoint, @NotNull Vector3dc centerOfMass) {
        AxisAlignedBB bounds = entity.getEntityBoundingBox();
        double remainingTick = entity.ticksExisted == 0 ? 0D : 1D - partialTick;
        double previousYaw = entity.prevRotationYaw;
        if (entity instanceof EntityLivingBase livingEntity) previousYaw = livingEntity.prevRenderYawOffset;
        double yaw = previousYaw + MathHelper.wrapDegrees(getEntityYaw(entity) - previousYaw) * partialTick;
        return this.createTransform(
                (bounds.minX + bounds.maxX) * 0.5D + (entity.lastTickPosX - entity.posX) * remainingTick,
                bounds.minY + (entity.lastTickPosY - entity.posY) * remainingTick,
                (bounds.minZ + bounds.maxZ) * 0.5D + (entity.lastTickPosZ - entity.posZ) * remainingTick,
                yaw, attachmentPoint, centerOfMass);
    }

    @NotNull
    public ShipTransform createTransform(
            double entityX, double entityY, double entityZ, double entityYaw,
            @NotNull Vector3dc attachmentPoint, @NotNull Vector3dc centerOfMass
    ) {
        Quaterniond entityRotation = new Quaterniond().rotationY(-Math.toRadians(entityYaw));
        Quaterniond shipRotation = new Quaterniond().rotationY(-Math.toRadians(entityYaw - this.initialYaw))
                .mul(new Quaterniond(this.rotationX, this.rotationY, this.rotationZ, this.rotationW));
        Vector3d position = entityRotation.transform(new Vector3d(this.offsetX, this.offsetY, this.offsetZ));
        position.add(entityX, entityY, entityZ);
        Vector3d centerOffset = centerOfMass.sub(attachmentPoint, new Vector3d());
        shipRotation.transform(centerOffset);
        position.add(centerOffset);
        return new ShipTransform(position, shipRotation, new Vector3d(centerOfMass));
    }
}
