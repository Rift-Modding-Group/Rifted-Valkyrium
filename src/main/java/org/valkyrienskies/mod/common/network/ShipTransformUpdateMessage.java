package org.valkyrienskies.mod.common.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IThreadListener;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.interpolation.ITransformInterpolator;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.mod.common.util.jackson.VSJacksonUtil;

import java.io.IOException;
import java.util.*;

public class ShipTransformUpdateMessage implements IMessage {

    private static final ObjectMapper serializer = VSJacksonUtil.getPacketMapper();
    final Map<UUID, Tuple<ShipTransform, AxisAlignedBB>> shipTransforms;
    int dimensionID;

    public ShipTransformUpdateMessage() {
        this.shipTransforms = new HashMap<>();
        this.dimensionID = -1;
    }

    public void addData(final UUID shipUUID, final ShipTransform shipTransform, final AxisAlignedBB alignedBB) {
        shipTransforms.put(shipUUID, new Tuple<>(shipTransform, alignedBB));
    }

    public void setDimensionID(int dimensionID) {
        this.dimensionID = dimensionID;
    }

    public int getDimensionID() {
        return dimensionID;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        int numberOfShips = packetBuffer.readInt();
        for (int i = 0; i < numberOfShips; i++) {
            UUID shipID = null;
            ShipTransform shipTransform = null;
            AxisAlignedBB axisAlignedBB = null;
            // Read the UUID
            {
                int bytesSize = packetBuffer.readInt();
                byte[] bytes = new byte[bytesSize];
                packetBuffer.readBytes(bytes);
                try {
                    shipID = serializer.readValue(bytes, UUID.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // Read the ship transform
            {
                int bytesSize = packetBuffer.readInt();
                byte[] bytes = new byte[bytesSize];
                packetBuffer.readBytes(bytes);
                try {
                    shipTransform = serializer.readValue(bytes, ShipTransform.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            // Read the ship aabb
            {
                int bytesSize = packetBuffer.readInt();
                byte[] bytes = new byte[bytesSize];
                packetBuffer.readBytes(bytes);
                try {
                    axisAlignedBB = serializer.readValue(bytes, AxisAlignedBB.class);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (shipID == null || shipTransform == null || axisAlignedBB == null) {
                // corrupt packet
                shipTransforms.clear();
                return;
            }
            shipTransforms.put(shipID, new Tuple<>(shipTransform, axisAlignedBB));
        }
        dimensionID = packetBuffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(shipTransforms.size());
        for (Map.Entry<UUID, Tuple<ShipTransform, AxisAlignedBB>> data : shipTransforms.entrySet()) {
            // Write index data to the byte buffer.
            try {
                // Write the UUID
                {
                    byte[] dataBytes = serializer.writeValueAsBytes(data.getKey());
                    int bytesSize = dataBytes.length;
                    packetBuffer.writeInt(bytesSize);
                    packetBuffer.writeBytes(dataBytes);
                }
                // Write the ship transform
                {
                    byte[] dataBytes = serializer.writeValueAsBytes(data.getValue().getFirst());
                    int bytesSize = dataBytes.length;
                    packetBuffer.writeInt(bytesSize);
                    packetBuffer.writeBytes(dataBytes);
                }
                // Write the ship aabb
                {
                    byte[] dataBytes = serializer.writeValueAsBytes(data.getValue().getSecond());
                    int bytesSize = dataBytes.length;
                    packetBuffer.writeInt(bytesSize);
                    packetBuffer.writeBytes(dataBytes);
                }
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        packetBuffer.writeInt(dimensionID);
    }

    public static class Handler implements IMessageHandler<ShipTransformUpdateMessage, IMessage> {
        @Override
        @SuppressWarnings("Convert2Lambda")
        // Why do you not use a lambda? Because lambdas are compiled and this causes NoClassDefFound
        // errors. DON'T USE A LAMBDA
        public IMessage onMessage(final ShipTransformUpdateMessage message, final MessageContext ctx) {
            IThreadListener mainThread = Minecraft.getMinecraft();
            mainThread.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    World world = Minecraft.getMinecraft().world;
                    QueryableShipData worldData = QueryableShipData.get(world);

                    for (Map.Entry<UUID, Tuple<ShipTransform, AxisAlignedBB>> transformUpdate : message.shipTransforms.entrySet()) {
                        final UUID shipID = transformUpdate.getKey();
                        final ShipTransform shipTransform = transformUpdate.getValue().getFirst();
                        final AxisAlignedBB shipBB = transformUpdate.getValue().getSecond();

                        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
                        if (physObjectWorld == null) return;

                        final PhysicsObject physicsObject = physObjectWorld.getPhysObjectFromUUID(shipID);
                        if (physicsObject == null) return;

                        // Do not update the transform in ShipData, that will be done by PhysicsObject.tick()
                        ITransformInterpolator interpolator = physicsObject.getTransformInterpolator();
                        interpolator.onNewTransformPacket(shipTransform, shipBB);
                    }
                }
            });

            return null;
        }
    }
}
