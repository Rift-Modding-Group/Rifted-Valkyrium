package org.valkyrienskies.addon.control.nodenetwork;

import gigaherz.graph.api.Graph;
import gigaherz.graph.api.GraphObject;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.valkyrienskies.mod.common.network.VSNetwork;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import javax.annotation.Nullable;
import java.util.*;

public class VSNode_TileEntity implements IVSNode {
    private final TileEntity parentTile;
    //key is the blockpos of node it connects to, val is the wire that goes to that blockpos
    private final HashMap<BlockPos, EnumWireType> linkedNodesAndWireTypes;
    // A wrapper unmodifiable HashMap that allows external classes to see an immutable
    // version of linkedNodesAndWireTypes.
    private final Map<BlockPos, EnumWireType> immutableLinkedNodesAndWireTypes;
    private final int maximumConnections;
    private boolean isValid;
    private Graph nodeGraph;

    public VSNode_TileEntity(TileEntity parent, int maximumConnections) {
        this.parentTile = parent;
        this.linkedNodesAndWireTypes = new HashMap<>();
        this.immutableLinkedNodesAndWireTypes = Collections.unmodifiableMap(this.linkedNodesAndWireTypes);
        this.isValid = false;
        this.maximumConnections = maximumConnections;
        Graph.integrate(this, Collections.EMPTY_LIST,
            (graph) -> new BasicNodeTileEntity.GraphData());
    }

    @Nullable
    @Deprecated
    public static IVSNode getVSNode_TileEntity(World world, BlockPos pos) {
        if (world == null || pos == null) {
            throw new IllegalArgumentException("Null arguments");
        }
        boolean isChunkLoaded = world.isBlockLoaded(pos);
        if (!isChunkLoaded) {
            // throw new IllegalStateException("VSNode_TileEntity wasn't loaded in the world!");
            return null;
        }
        TileEntity entity = world.getTileEntity(pos);
        if (entity == null) {
            return null;
            // throw new IllegalStateException("VSNode_TileEntity was null");
        }
        if (entity instanceof IVSNodeProvider) {
            IVSNode vsNode = ((IVSNodeProvider) entity).getNode();
            if (!vsNode.isValid()) {
                return null;
                // throw new IllegalStateException("IVSNode was not valid!");
            } else {
                return vsNode;
            }
        }
        else {
            return null;
            // throw new IllegalStateException("VSNode_TileEntity of different class");
        }
    }

    @Override
    public Iterable<IVSNode> getDirectlyConnectedNodes() {
        // assertValidity();
        List<IVSNode> nodesList = new ArrayList<IVSNode>();
        for (BlockPos pos : this.linkedNodesAndWireTypes.keySet()) {
            IVSNode node = getVSNode_TileEntity(getNodeWorld(), pos);
            if (node != null) nodesList.add(node);
        }
        return nodesList;
    }

    @Override
    public void makeConnection(IVSNode other, EnumWireType wireType) {
        this.assertValidity();
        if (!this.linkedNodesAndWireTypes.containsKey(other.getNodePos())) {
            this.linkedNodesAndWireTypes.put(other.getNodePos(), wireType);
            this.parentTile.markDirty();
            other.makeConnection(this, wireType);
            this.sendNodeUpdates();
            List<GraphObject> stupid = Collections.singletonList(other);
            getGraph().addNeighours(this, stupid);
        }
    }

    @Override
    public void breakConnection(IVSNode other) {
        assertValidity();
        if (this.linkedNodesAndWireTypes.containsKey(other.getNodePos())) {
            this.linkedNodesAndWireTypes.remove(other.getNodePos());
            this.parentTile.markDirty();
            other.breakConnection(this);
            this.sendNodeUpdates();
            try {
                // TODO: For some reason null graphs show up. Not sure why, but it seems safe to just ignore them.
                if (this.getGraph() != null) {
                    this.getGraph().removeNeighbour(this, other);
                }
            }
            catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public BlockPos getNodePos() {
        this.assertValidity();
        return this.parentTile.getPos();
    }

    @Override
    public void validate() {
        this.isValid = true;
    }

    @Override
    public void invalidate() {
        this.isValid = false;
    }

    @Override
    public boolean isValid() {
        return this.isValid;
    }

    @Override
    public World getNodeWorld() {
        return this.parentTile.getWorld();
    }

    @Override
    public Map<BlockPos, EnumWireType> getLinkedNodesAndWireTypes() {
        return this.immutableLinkedNodesAndWireTypes;
    }

    @Override
    public void writeToNBT(NBTTagCompound compound) {
        int[] data = new int[this.linkedNodesAndWireTypes.size() * 4];
        int i = 0;
        for (Map.Entry<BlockPos, EnumWireType> entry : this.linkedNodesAndWireTypes.entrySet()) {
            data[i++] = entry.getKey().getX();
            data[i++] = entry.getKey().getY();
            data[i++] = entry.getKey().getZ();
            data[i++] = entry.getValue().ordinal();
        }
        compound.setIntArray(NBT_DATA_KEY, data);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        this.linkedNodesAndWireTypes.clear();
        int[] data = compound.getIntArray(NBT_DATA_KEY);
        for (int i = 0; i < data.length; i += 4) {
            BlockPos blockPos = new BlockPos(data[i], data[i + 1], data[i + 2]);
            EnumWireType enumWireType = EnumWireType.values()[data[i + 3]];
            this.linkedNodesAndWireTypes.put(blockPos, enumWireType);
        }
    }

    @Override
    public void sendNodeUpdates() {
        if (!this.getNodeWorld().isRemote) {
            // System.out.println("help");
            if (!this.parentTile.isInvalid()) {
                VSNetwork.sendTileToAllNearby(this.parentTile);
            }
        }
    }

    private void assertValidity() {
        if (!isValid()) {
            throw new IllegalStateException(
                "This node at " + parentTile.getPos() + " is not valid / initialized!");
        }
    }

    @Override
    public void shiftConnections(BlockPos offset) {
        if (this.isValid()) {
            throw new IllegalStateException(
                "Cannot shift the connections of a Node while it is valid and in use!");
        }
        HashMap<BlockPos, EnumWireType> shiftedNodesAndWireTypes = new HashMap<>();
        for (Map.Entry<BlockPos, EnumWireType> entry : this.linkedNodesAndWireTypes.entrySet()) {
            shiftedNodesAndWireTypes.put(entry.getKey().add(offset), entry.getValue());
        }
        this.linkedNodesAndWireTypes.clear();
        this.linkedNodesAndWireTypes.putAll(shiftedNodesAndWireTypes);
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        else if (other instanceof VSNode_TileEntity otherNode) {
            return otherNode.getNodePos().equals(this.getNodePos());
        }
        return false;
    }

    @Override
    public Graph getGraph() {
        return nodeGraph;
    }

    @Override
    public void setGraph(Graph graph) {
        this.nodeGraph = graph;
    }

    @Override
    public List<GraphObject> getNeighbours() {
        List<GraphObject> nodesList = new ArrayList<GraphObject>();
        for (BlockPos pos : this.linkedNodesAndWireTypes.keySet()) {
            IVSNode node = getVSNode_TileEntity(getNodeWorld(), pos);
            if (node != null) {
                if (node.getGraph() == null) {
                    System.err.println("Graph node at " + node.getNodePos() + " was missing a graph! So we added one.");
                    Graph.integrate(node, Collections.EMPTY_LIST, (graph) -> new BasicNodeTileEntity.GraphData());
                }
                nodesList.add(node);
            }
        }
        return nodesList;
    }

    @Override
    public TileEntity getParentTile() {
        return this.parentTile;
    }

    @Override
    public int getMaximumConnections() {
        return maximumConnections;
    }
}
