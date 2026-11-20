package org.valkyrienskies.mod.common.compat;

import anightdazingzoroark.riftlib.core.IAnimatable;
import anightdazingzoroark.riftlib.core.manager.AbstractAnimationDataAdvancedEntity;
import anightdazingzoroark.riftlib.model.AnimatedLocator;
import anightdazingzoroark.riftlib.molang.utils.Interpolations;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaterniond;
import org.joml.Quaternionf;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.ships.ShipEntityAttachment;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;

/**
 * Optional support for links to animated model locators.
 * */
public class RiftLibraryCompat {
    @Nullable
    public static ShipTransform createAttachmentTransform(
            @NotNull Entity entity, @NotNull ShipEntityAttachment attachment, double partialTick,
            @NotNull Vector3dc attachmentPoint, @NotNull Vector3dc centerOfMass
    ) {
        if (!(entity instanceof IAnimatable<?> animatable)
                || !(animatable.getAnimationData() instanceof AbstractAnimationDataAdvancedEntity<?, ?> data)
                || attachment.locator() == null
        ) {
            return null;
        }
        AnimatedLocator locator = data.getAnimatedLocator(attachment.locator());
        if (locator == null) return null;

        Vec3d modelPosition = locator.getModelSpacePosition();
        double scale = data.getScale() / 16D;
        Quaterniond heading = new Quaterniond().rotationY(-Math.toRadians(data.getModelYaw((float) partialTick)));
        Vector3d position = heading.transform(new Vector3d(-modelPosition.x * scale, modelPosition.y * scale, -modelPosition.z * scale));
        position.add(
                Interpolations.lerp(entity.prevPosX, entity.posX, partialTick),
                Interpolations.lerp(entity.prevPosY, entity.posY, partialTick),
                Interpolations.lerp(entity.prevPosZ, entity.posZ, partialTick)
        );
        Quaternionf modelRotation = locator.getModelSpaceYXZQuaternion();
        Quaterniond rotation = heading.mul(new Quaterniond(-modelRotation.x, modelRotation.y, -modelRotation.z, modelRotation.w)).normalize();
        Vector3d centerOffset = centerOfMass.sub(attachmentPoint, new Vector3d()).add(attachment.offsetX(), attachment.offsetY(), attachment.offsetZ());
        rotation.transform(centerOffset);
        position.add(centerOffset);
        return new ShipTransform(position, rotation, new Vector3d(centerOfMass));
    }
}
