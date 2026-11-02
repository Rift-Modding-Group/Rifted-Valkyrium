package org.valkyrienskies.mod.common.network;

import net.minecraft.network.PacketBuffer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.ShipLocalEntityMovementData;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import valkyrienwarfare.api.TransformType;

import java.util.UUID;

/**
 * Vehicle coordinates sampled in the client's ship frame, independent of the vehicle's entity type.
 */
public record VehicleMovementData(@NotNull UUID shipId, @NotNull Vector3dc localPosition, double localYaw) {
    public record WorldMovement(@NotNull Vector3dc position, float yaw) {}

    @NotNull
    public static VehicleMovementData fromWorld(@NotNull UUID shipId, @NotNull ShipTransform transform, @NotNull Vector3dc position, double yaw) {
        Vector3d localPosition = new Vector3d(position);
        transform.transformPosition(localPosition, TransformType.GLOBAL_TO_SUBSPACE);
        double localYaw = ShipLocalEntityMovementData.transformYaw(transform, yaw, TransformType.GLOBAL_TO_SUBSPACE);
        return new VehicleMovementData(shipId, localPosition, localYaw);
    }

    @NotNull
    public static WorldMovement toWorld(@NotNull VehicleMovementData movement, @NotNull ShipTransform transform) {
        Vector3d position = new Vector3d(movement.localPosition);
        transform.transformPosition(position, TransformType.SUBSPACE_TO_GLOBAL);
        float yaw = (float) ShipLocalEntityMovementData.transformYaw(transform, movement.localYaw, TransformType.SUBSPACE_TO_GLOBAL);
        return new WorldMovement(position, yaw);
    }

    public static void write(@NotNull PacketBuffer buffer, @Nullable VehicleMovementData movement) {
        buffer.writeBoolean(movement != null);
        if (movement == null) return;

        buffer.writeUniqueId(movement.shipId);
        buffer.writeDouble(movement.localPosition.x());
        buffer.writeDouble(movement.localPosition.y());
        buffer.writeDouble(movement.localPosition.z());
        buffer.writeDouble(movement.localYaw);
    }

    @Nullable
    public static VehicleMovementData read(@NotNull PacketBuffer buffer) {
        if (!buffer.readBoolean()) return null;

        UUID shipId = buffer.readUniqueId();
        Vector3d localPosition = new Vector3d(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        return new VehicleMovementData(shipId, localPosition, buffer.readDouble());
    }
}
