package org.valkyrienskies.addon.control.block.multiblocks;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.valkyrienskies.addon.control.MultiblockRegistry;
import org.valkyrienskies.addon.control.nodenetwork.BasicNodeTileEntity;
import org.valkyrienskies.mod.common.network.VSNetwork;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

/**
 * Just a simple implementation of the interfaces.
 *
 * @param <E> The type of schematic for this TileEntity to use.
 * @param <F> The type of class extending this class.
 */
public abstract class TileEntityMultiblockPart<E extends IMultiblockSchematic, F extends TileEntityMultiblockPart<?, ?>> extends BasicNodeTileEntity implements ITileEntityMultiblockPart<E, F> {
    private boolean isAssembled;
    private boolean isMaster;
    // The relative position of this tile to its master.
    private BlockPos offsetPos;
    private E multiblockSchematic;

    public TileEntityMultiblockPart() {
        super();
        this.isAssembled = false;
        this.isMaster = false;
        this.offsetPos = BlockPos.ORIGIN;
        this.multiblockSchematic = null;
    }

    @Override
    public boolean isPartOfAssembledMultiblock() {
        return this.isAssembled;
    }

    @Override
    public boolean isMaster() {
        return this.isMaster;
    }

    @Override
    public F getMaster() {
        TileEntity masterTile = ValkyrienUtils.getTileEntitySafe(this.getWorld(), this.getMultiblockOrigin());
        if (masterTile instanceof ITileEntityMultiblockPart) {
            return (F) masterTile;
        }
        return null;
    }

    @Override
    public BlockPos getMultiblockOrigin() {
        return this.getPos().subtract(this.offsetPos);
    }

    @Override
    public BlockPos getRelativePos() {
        return offsetPos;
    }

    @Override
    public void disassembleMultiblock() {
        if (this.multiblockSchematic == null) return;
        for (ImmutablePair<BlockPos, Block> pair : this.multiblockSchematic.getStructureRelativeToCenter()) {
            BlockPos posToBreak = pair.getLeft().add(getMultiblockOrigin());
            TileEntity tileToBreak = this.getWorld().getTileEntity(posToBreak);
            if (tileToBreak instanceof ITileEntityMultiblockPart<?, ?> tileMultiblockPart) {
                tileMultiblockPart.disassembleMultiblockLocal();
            }
        }
    }

    @Override
    public void disassembleMultiblockLocal() {
        this.isAssembled = false;
        this.isMaster = false;
        this.multiblockSchematic = null;
        IBlockState blockState = this.getWorld().getBlockState(this.getPos());
        this.getWorld().notifyBlockUpdate(this.getPos(), blockState, blockState, 2);
        this.markDirty();
    }

    @Override
    public void assembleMultiblock(E schematic, BlockPos relativePos) {
        this.isAssembled = true;
        this.isMaster = relativePos.equals(BlockPos.ORIGIN);
        this.offsetPos = relativePos;
        this.multiblockSchematic = schematic;
        IBlockState blockState = this.getWorld().getBlockState(this.getPos());
        this.getWorld().notifyBlockUpdate(this.getPos(), blockState, blockState, 2);
        this.markDirty();
    }

    @Override
    public E getMultiBlockSchematic() {
        return this.multiblockSchematic;
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        NBTTagCompound toReturn = super.writeToNBT(compound);
        toReturn.setBoolean("isAssembled", this.isAssembled);
        toReturn.setBoolean("isMaster", this.isMaster);
        toReturn.setInteger("offsetPosX", this.offsetPos.getX());
        toReturn.setInteger("offsetPosY", this.offsetPos.getY());
        toReturn.setInteger("offsetPosZ", this.offsetPos.getZ());
        if (this.multiblockSchematic != null) {
            toReturn.setString("multiblockSchematicID", this.multiblockSchematic.getSchematicID());
        }
        else toReturn.setString("multiblockSchematicID", "unknown");
        return toReturn;
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        super.readFromNBT(compound);
        this.isAssembled = compound.getBoolean("isAssembled");
        this.isMaster = compound.getBoolean("isMaster");
        this.offsetPos = new BlockPos(
                compound.getInteger("offsetPosX"),
                compound.getInteger("offsetPosY"),
                compound.getInteger("offsetPosZ")
        );
        this.multiblockSchematic = (E) MultiblockRegistry.getSchematicByID(compound.getString("multiblockSchematicID"));
    }

    @Override
    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        if (this.isPartOfAssembledMultiblock()) {
            return getMultiBlockSchematic().getSchematicRenderBB(getMultiblockOrigin());
        }
        return super.getRenderBoundingBox();
    }

}
