package org.valkyrienskies.mod.common.network;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.IThreadListener;
import net.minecraft.world.World;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.mod.common.util.jackson.VSJacksonUtil;

import java.io.IOException;
import java.util.*;

/**
 * Sends ShipData updates to the client, also tells it which ShipData to convert load/unload as PhysicsObject.
 */
public class ShipIndexDataMessage implements IMessage {

    private static final ObjectMapper serializer = VSJacksonUtil.getPacketMapper();
    final List<ShipData> indexedData;
    final List<UUID> shipsToLoad, shipsToUnload;
    int dimensionID;

    public ShipIndexDataMessage() {
        this.indexedData = new ArrayList<>();
        this.shipsToLoad = new ArrayList<>();
        this.shipsToUnload = new ArrayList<>();
        this.dimensionID = -1;
    }

    public void addData(Collection<ShipData> toSend) {
        indexedData.addAll(toSend);
    }

    public void addLoadUUID(UUID toLoad) {
        shipsToLoad.add(toLoad);
    }

    public void addUnloadUUID(UUID toUnload) {
        shipsToUnload.add(toUnload);
    }

    public void setDimensionID(int dimensionID) {
        this.dimensionID = dimensionID;
    }

    @Override
    public void fromBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        int numberOfIndices = packetBuffer.readInt();
        int numberOfUUIDLoad = packetBuffer.readInt();
        int numberOfUUIDUnload = packetBuffer.readInt();
        for (int i = 0; i < numberOfIndices; i++) {
            // Read index data from the byte buffer.
            int bytesSize = packetBuffer.readInt();
            byte[] bytes = new byte[bytesSize];
            packetBuffer.readBytes(bytes);
            try {
                ShipData data = serializer.readValue(bytes, ShipData.class);
                this.indexedData.add(data);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        for (int i = 0; i < numberOfUUIDLoad; i++) {
            shipsToLoad.add(packetBuffer.readUniqueId());
        }
        for (int i = 0; i < numberOfUUIDUnload; i++) {
            shipsToUnload.add(packetBuffer.readUniqueId());
        }
        dimensionID = packetBuffer.readInt();
    }

    @Override
    public void toBytes(ByteBuf buf) {
        PacketBuffer packetBuffer = new PacketBuffer(buf);
        packetBuffer.writeInt(indexedData.size());
        packetBuffer.writeInt(shipsToLoad.size());
        packetBuffer.writeInt(shipsToUnload.size());
        for (ShipData data : indexedData) {
            // Write index data to the byte buffer.
            try {
                byte[] dataBytes = serializer.writeValueAsBytes(data);
                int bytesSize = dataBytes.length;
                packetBuffer.writeInt(bytesSize);
                packetBuffer.writeBytes(dataBytes);
            } catch (JsonProcessingException e) {
                e.printStackTrace();
            }
        }
        for (UUID toLoad : shipsToLoad) {
            packetBuffer.writeUniqueId(toLoad);
        }
        for (UUID toUnload : shipsToUnload) {
            packetBuffer.writeUniqueId(toUnload);
        }
        packetBuffer.writeInt(dimensionID);
    }

    public static class Handler implements IMessageHandler<ShipIndexDataMessage, IMessage> {

        @Override
        @SuppressWarnings("Convert2Lambda")
        // Why do you not use a lambda? Because lambdas are compiled and this causes NoClassDefFound
        // errors. DON'T USE A LAMBDA
        public IMessage onMessage(ShipIndexDataMessage message, MessageContext ctx) {
            IThreadListener mainThread = Minecraft.getMinecraft();
            mainThread.addScheduledTask(new Runnable() {
                @Override
                public void run() {
                    World world = Minecraft.getMinecraft().world;
                    IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
                    if (physObjectWorld == null) return;
                    QueryableShipData worldData = QueryableShipData.get(world);
                    for (ShipData shipData : message.indexedData) {
                        worldData.addOrUpdateShipPreservingPhysObj(shipData, world);
                    }
                    for (UUID loadID : message.shipsToLoad) {
                        physObjectWorld.queueShipLoad(loadID);
                    }
                    for (UUID unloadID : message.shipsToUnload) {
                        physObjectWorld.queueShipUnload(unloadID);
                    }
                }
            });

            return null;
        }
    }
}
