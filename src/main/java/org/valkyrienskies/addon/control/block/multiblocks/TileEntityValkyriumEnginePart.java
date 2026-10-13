package org.valkyrienskies.addon.control.block.multiblocks;

import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.valkyrienskies.addon.control.MultiblockRegistry;
import org.valkyrienskies.addon.control.block.torque.IRotationNode;
import org.valkyrienskies.addon.control.block.torque.IRotationNodeProvider;
import org.valkyrienskies.addon.control.block.torque.IRotationNodeWorld;
import org.valkyrienskies.addon.control.block.torque.ImplRotationNode;
import org.valkyrienskies.addon.control.block.torque.custom_torque_functions.ValkyriumEngineTorqueFunction;
import org.valkyrienskies.addon.control.util.ValkyrienSkiesControlUtil;
import org.valkyrienskies.mod.common.network.VSNetwork;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.VSMath;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.List;
import java.util.Optional;

public class TileEntityValkyriumEnginePart extends TileEntityMultiblockPart<ValkyriumEngineMultiblockSchematic, TileEntityValkyriumEnginePart> implements IRotationNodeProvider<TileEntityValkyriumEnginePart> {

    private static final int ROTATION_NODE_SORT_PRIORITY = 10000;
    @SuppressWarnings("WeakerAccess")
    protected final IRotationNode rotationNode;
    private double prevKeyframe;
    private double currentKeyframe;
    private double nextKeyframe;
    private boolean firstUpdate;

    @SuppressWarnings("WeakerAccess")
    public TileEntityValkyriumEnginePart() {
        super();
        this.prevKeyframe = 0;
        this.currentKeyframe = 0;
        this.rotationNode = new ImplRotationNode<>(this, 50, ROTATION_NODE_SORT_PRIORITY);
        this.firstUpdate = true;
    }

    @Override
    public void update() {
        super.update();
        if (!this.getWorld().isRemote) {
            if (this.firstUpdate) {
                this.rotationNode.markInitialized();
                this.firstUpdate = false;
            }

            if (this.isPartOfAssembledMultiblock()) {
                Optional<PhysicsObject> physicsObjectOptional = ValkyrienUtils.getPhysoManagingBlock(this.getWorld(), this.getPos());

                IRotationNodeWorld nodeWorld = physicsObjectOptional.map(ValkyrienSkiesControlUtil::getRotationWorldFromShip)
                        .orElseGet(() -> ValkyrienSkiesControlUtil.getRotationWorldFromWorld(getWorld()));
                if (physicsObjectOptional.isPresent() && !this.rotationNode.hasBeenPlacedIntoNodeWorld()
                    && this.getRelativePos().equals(this.getMultiBlockSchematic().getTorqueOutputPos())
                ) {
                    nodeWorld.enqueueTaskOntoWorld(
                        () -> nodeWorld.setNodeFromPos(this.getPos(), this.rotationNode)
                    );
                }

                BlockPos torqueOutputPos = this.getMultiBlockSchematic().getTorqueOutputPos()
                    .add(this.getPos());
                TileEntity tileEntity = this.getWorld().getTileEntity(torqueOutputPos);
                if (tileEntity instanceof TileEntityValkyriumEnginePart) {
                    if (((TileEntityValkyriumEnginePart) tileEntity).getRotationNode()
                        .isPresent()) {
                        prevKeyframe = currentKeyframe;
                        double radiansRotatedThisTick =
                            ((TileEntityValkyriumEnginePart) tileEntity).getRotationNode().get()
                                .getAngularVelocityUnsynchronized() / 20D;
                        // Thats about right, although the x1.3 multiplier tells me the world node math is wrong.
                        this.currentKeyframe += radiansRotatedThisTick * 99D / (6D * Math.PI);
                        this.currentKeyframe = this.currentKeyframe % 99;
                    }
                }
                if (this.isMaster()) {
                    IBlockState blockState = this.getWorld().getBlockState(this.getPos());
                    this.getWorld().notifyBlockUpdate(this.getPos(), blockState, blockState, 2);
                }
            }
            this.markDirty();
        }
        else {
            // Client keyframe interpolating logic, use .85 to smoothly slide towards actual value
            // to appear more fluid when the server lags.
            this.prevKeyframe = this.currentKeyframe;
            this.currentKeyframe = VSMath.interpolateModulatedNumbers(this.currentKeyframe, this.nextKeyframe, 0.85, 99);
        }
    }

    public double getCurrentKeyframe(double partialTick) {
        return VSMath.interpolateModulatedNumbers(this.prevKeyframe, this.currentKeyframe, partialTick, 99);
    }

    @Override
    public void assembleMultiblock(ValkyriumEngineMultiblockSchematic schematic, BlockPos relativePos) {
        super.assembleMultiblock(schematic, relativePos);
        if (relativePos.equals(schematic.getTorqueOutputPos())) {
            Optional<PhysicsObject> objectOptional = ValkyrienUtils.getPhysoManagingBlock(getWorld(), getPos());
            IRotationNodeWorld nodeWorld = objectOptional.map(ValkyrienSkiesControlUtil::getRotationWorldFromShip)
                    .orElseGet(() -> ValkyrienSkiesControlUtil.getRotationWorldFromWorld(getWorld()));
            EnumFacing facing = EnumFacing.getFacingFromVector(
                    schematic.getTorqueOutputDirection().getX(),
                    schematic.getTorqueOutputDirection().getY(),
                    schematic.getTorqueOutputDirection().getZ()
            );
            assert getRotationNode().isPresent() : "How the heck did we try assembling the multiblock without a rotation node initialized!";

            this.rotationNode.queueTask(() -> {
                this.rotationNode.setAngularVelocityRatio(facing, Optional.of(-1D));
                this.rotationNode.setCustomTorqueFunction(new ValkyriumEngineTorqueFunction(this.rotationNode));
            });
            nodeWorld.enqueueTaskOntoWorld(() -> nodeWorld.setNodeFromPos(this.pos, this.rotationNode));
        }
    }

    @Override
    public boolean attemptToAssembleMultiblock(World worldIn, BlockPos pos, EnumFacing facing) {
        List<IMultiblockSchematic> schematics = MultiblockRegistry.getSchematicsWithPrefix("multiblock_valkyrium_engine");
        for (IMultiblockSchematic schematic : schematics) {
            if (schematic.attemptToCreateMultiblock(worldIn, pos)) return true;
        }
        return false;
    }

    @Override
    public void disassembleMultiblockLocal() {
        super.disassembleMultiblockLocal();
        Optional<PhysicsObject> object = ValkyrienUtils.getPhysoManagingBlock(getWorld(), getPos());
        if (object.isPresent()) this.rotationNode.queueTask(this.rotationNode::resetNodeData);
    }

    // The following methods are basically just here because interfaces can't have fields.
    @Override
    public Optional<IRotationNode> getRotationNode() {
        if (this.rotationNode.isInitialized()) return Optional.of(this.rotationNode);
        return Optional.empty();
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        if (this.getWorld() == null || !this.getWorld().isRemote) {
            this.rotationNode.readFromNBT(compound);
        }
//        rotationNode.markInitialized();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        super.writeToNBT(compound);
        this.rotationNode.writeToNBT(compound);
        return compound;
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        NBTTagCompound tagToSend = super.getUpdateTag();
        tagToSend.setDouble("currentKeyframe", this.currentKeyframe);
        return new SPacketUpdateTileEntity(this.getPos(), 0, tagToSend);
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        super.onDataPacket(net, pkt);
        this.nextKeyframe = pkt.getNbtCompound().getDouble("currentKeyframe");
    }
}
