package org.valkyrienskies.addon.control.tileentity;

import net.minecraft.block.Block;
import net.minecraft.block.state.IBlockState;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SPacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ITickable;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.ItemStackHandler;
import org.jspecify.annotations.NonNull;
import org.valkyrienskies.addon.control.ValkyrienSkiesControl;
import org.valkyrienskies.addon.control.block.BlockPhysicsInfuser;
import org.valkyrienskies.addon.control.container.EnumInfuserButton;
import org.valkyrienskies.addon.control.gui.IVSTileGui;
import org.valkyrienskies.addon.control.network.VSGuiButtonMessage;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.block_relocation.BlockFinder;
import org.valkyrienskies.mod.common.ships.block_relocation.IRelocationAwareTile;
import org.valkyrienskies.mod.common.ships.chunk_claims.ShipChunkAllocator;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.IPhysicsEntity;
import valkyrienwarfare.api.IShipMaintenanceProvider;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class TileEntityPhysicsInfuser extends TileEntity implements ITickable, ICapabilityProvider, IVSTileGui, IRelocationAwareTile, IShipMaintenanceProvider {
    private static final String ACTION_STATE_NBT_KEY = "action_state";
    private static final int UNMAINTAINED_TICKS_BEFORE_DISASSEMBLY = 20;
    private final ItemStackHandler handler;
    private volatile boolean sendUpdateToClients;
    private volatile boolean isTryingToAssembleShip;
    private volatile boolean isTryingToDisassembleShip;
    private EnumInfuserAction actionState;
    private boolean isPhysicsEnabled;
    private boolean isTryingToAlignShip;
    // Used by the client to store the vertical offset of each core
    private final Map<EnumInfuserCore, Double> coreOffsets;
    private final Map<EnumInfuserCore, Double> coreOffsetsPrevTick;
    private int disassembleCounter; // # of ticks to wait before resetting isTryingToDisassembleShip
    private int unmaintainedTicks;
    @Nullable
    private IPhysicsEntity maintenanceShip;

    public TileEntityPhysicsInfuser() {
        this.handler = new ItemStackHandler(EnumInfuserCore.values().length) {
            @Override
            protected void onContentsChanged(int slot) {
                TileEntityPhysicsInfuser.this.sendUpdateToClients = true;
            }
        };
        this.sendUpdateToClients = false;
        this.isTryingToAssembleShip = false;
        this.isTryingToDisassembleShip = false;
        this.actionState = EnumInfuserAction.IDLE;
        this.isPhysicsEnabled = false;
        this.isTryingToAlignShip = false;
        this.coreOffsets = new HashMap<>();
        this.coreOffsetsPrevTick = new HashMap<>();
        for (EnumInfuserCore enumInfuserCore : EnumInfuserCore.values()) {
            this.coreOffsets.put(enumInfuserCore, 0D);
            this.coreOffsetsPrevTick.put(enumInfuserCore, 0D);
        }
        this.disassembleCounter = 0;
        this.unmaintainedTicks = 0;
        this.maintenanceShip = null;
    }

    @Override
    public void update() {
        // Check if we have to create a ship
        if (!getWorld().isRemote) {
            final Optional<PhysicsObject> parentShip = ValkyrienUtils.getPhysoManagingBlock(getWorld(), getPos());

            // Update the blockstate lighting
            final IBlockState infuserState = getWorld().getBlockState(getPos());
            final Block infuserBlock = infuserState.getBlock();
            if (!(infuserBlock instanceof BlockPhysicsInfuser)) {
                updateMaintenanceRegistration(null);
                System.err.println("Infuser block wasn't a physics infuser block?");
                return;
            }
            updateMaintenanceRegistration(parentShip.orElse(null));
            final BlockFinder.BlockFinderType blockFinderType = ((BlockPhysicsInfuser) infuserBlock).getBlockFinderType();
            if (infuserState.getBlock() == ValkyrienSkiesControl.INSTANCE.vsControlBlocks.physicsInfuser) {
                if (isPhysicsEnabled() && canMaintainShip()) {
                    if (!infuserState.getValue(BlockPhysicsInfuser.INFUSER_LIGHT_ON)) {
                        IBlockState newState = infuserState
                            .withProperty(BlockPhysicsInfuser.INFUSER_LIGHT_ON, true);
                        getWorld().setBlockState(getPos(), newState);
                    }
                }
                else {
                    if (infuserState.getValue(BlockPhysicsInfuser.INFUSER_LIGHT_ON)) {
                        IBlockState newState = infuserState.withProperty(BlockPhysicsInfuser.INFUSER_LIGHT_ON, false);
                        this.getWorld().setBlockState(getPos(), newState);
                    }
                }
            }
            // Check the status of the item slots
            if (parentShip.isEmpty() && canMaintainShip() && this.isTryingToAssembleShip) {
                // Create a ship with this physics infuser
                // Make sure we don't try to create a ship when we're already in ship space.
                if (!ShipChunkAllocator.isBlockInShipyard(getPos())) {
                    try {
                        ValkyrienUtils.assembleShipAsOrderedByPlayer(getWorld(), null, getPos(), blockFinderType);
                    }
                    catch (Exception e) {
                        e.printStackTrace();
                    }
                    // Also tell the watching players to open the new guy
                    // BlockPos newInfuserPos = ship.getPhysicsObject().getPhysicsInfuserPos();
                }
            }
            else if (actionState == EnumInfuserAction.ASSEMBLING && !isTryingToAssembleShip) {
                // A successful assembly removes this tile before its next tick. If it still exists,
                // the queued assembly was rejected, so allow the player to try again.
                setActionState(EnumInfuserAction.IDLE);
            }

            boolean shipIsMaintained = canMaintainShip() || parentShip.map(PhysicsObject::hasActiveMaintenanceProvider).orElse(false);
            if (parentShip.isPresent() && !shipIsMaintained) {
                this.unmaintainedTicks = Math.min(this.unmaintainedTicks + 1, UNMAINTAINED_TICKS_BEFORE_DISASSEMBLY);
            }
            else {
                this.unmaintainedTicks = 0;
            }

            // Preserve legacy automatic disassembly, but give relocated providers time to register.
            if (parentShip.isPresent() && this.unmaintainedTicks >= UNMAINTAINED_TICKS_BEFORE_DISASSEMBLY) {
                PhysicsObject physicsObject = parentShip.get();
                this.isTryingToAlignShip = true;
                this.isTryingToDisassembleShip = true;
                physicsObject.setPhysicsEnabled(true);
                physicsObject.setShipAligningToGrid(this.isTryingToAlignShip);
                if (this.isTryingToDisassembleShip) {
                    physicsObject.setDeconstructState(PhysicsObject.DeconstructState.DECONSTRUCT_NORMAL);
                }
                else {
                    physicsObject.setDeconstructState(PhysicsObject.DeconstructState.NOT_DECONSTRUCTING);
                }
            }

            // Send any updates to clients
            if (this.sendUpdateToClients) {
                IBlockState blockState = this.getWorld().getBlockState(this.getPos());
                this.getWorld().notifyBlockUpdate(this.getPos(), blockState, blockState, 2);
                this.sendUpdateToClients = false;
            }
        }
        else {
            // Client code to produce the physics infuser core wave effect
            if (this.isPhysicsEnabled()) {
                long worldTime = getWorld().getWorldTime();
                for (EnumInfuserCore enumInfuserCore : EnumInfuserCore.values()) {
                    coreOffsetsPrevTick.put(enumInfuserCore, coreOffsets.get(enumInfuserCore));
                    if (!handler.getStackInSlot(enumInfuserCore.coreSlotIndex)
                        .isEmpty() && canMaintainShip()) {
                        double sinAngle = ((worldTime % 50) * 2 * Math.PI / 50)
                            + (2 * Math.PI / EnumInfuserCore.values().length)
                            * enumInfuserCore.coreSlotIndex;
                        double idealOffset = 0.025D * (Math.sin(sinAngle) + 1);
                        double lerpedOffset = 0.9D * this.coreOffsets.get(enumInfuserCore) + .1 * idealOffset;
                        coreOffsets.put(enumInfuserCore, lerpedOffset);
                    }
                    else {
                        this.coreOffsets.put(enumInfuserCore, 0D);
                    }
                }
            }
            else {
                for (EnumInfuserCore enumInfuserCore : EnumInfuserCore.values()) {
                    this.coreOffsetsPrevTick.put(enumInfuserCore, this.coreOffsets.get(enumInfuserCore));
                    double lerpedOffset = 0.95D * this.coreOffsets.get(enumInfuserCore);
                    this.coreOffsets.put(enumInfuserCore, lerpedOffset);
                }
            }
        }

        // Always set tryToAssembleShip and tryToDisassembleShip to false, they only have 1 tick to try to act.
        this.isTryingToAssembleShip = false;
        if (this.disassembleCounter == 0) this.isTryingToDisassembleShip = false;
    }

    @Override
    public boolean canMaintainShip() {
        ItemStack mainStack = this.handler.getStackInSlot(EnumInfuserCore.MAIN.coreSlotIndex);
        return !mainStack.isEmpty();
    }

    private void updateMaintenanceRegistration(@Nullable IPhysicsEntity currentShip) {
        if (this.maintenanceShip == currentShip) return;
        this.unregisterMaintenanceProvider();
        if (currentShip != null) {
            currentShip.addMaintenanceProvider(this);
            this.maintenanceShip = currentShip;
        }
        this.unmaintainedTicks = 0;
    }

    private void unregisterMaintenanceProvider() {
        if (this.maintenanceShip == null) return;
        this.maintenanceShip.removeMaintenanceProvider(this);
        this.maintenanceShip = null;
        this.unmaintainedTicks = 0;
    }

    @Override
    public void invalidate() {
        this.unregisterMaintenanceProvider();
        super.invalidate();
    }

    @Override
    public void onChunkUnload() {
        unregisterMaintenanceProvider();
        super.onChunkUnload();
    }

    @Override
    public NBTTagCompound writeToNBT(NBTTagCompound compound) {
        compound.setTag("item_stack_handler", this.handler.serializeNBT());
        compound.setBoolean("physics_enabled", this.isPhysicsEnabled);
        compound.setBoolean("try_to_align_ship", this.isTryingToAlignShip);
        return super.writeToNBT(compound);
    }

    @Override
    public void readFromNBT(NBTTagCompound compound) {
        this.handler.deserializeNBT(compound.getCompoundTag("item_stack_handler"));
        this.isPhysicsEnabled = compound.getBoolean("physics_enabled");
        this.isTryingToAlignShip = compound.getBoolean("try_to_align_ship");
        super.readFromNBT(compound);
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T getCapability(@NonNull Capability<T> capability, EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return (T) handler;
        }
        return super.getCapability(capability, facing);
    }

    @Override
    public boolean hasCapability(@NonNull Capability<?> capability, @Nullable EnumFacing facing) {
        if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
            return true;
        }
        return super.hasCapability(capability, facing);
    }

    public boolean isUsableByPlayer(EntityPlayer player) {
        if (this.world.getTileEntity(this.pos) != this) return false;
        else {
            double distFromPlayer = player.getDistanceSq(
                    (double) this.pos.getX() + 0.5D,
                    (double) this.pos.getY() + 0.5D,
                    (double) this.pos.getZ() + 0.5D
            );
            return distFromPlayer <= 64D;
        }
    }

    @Override
    @Nonnull
    public NBTTagCompound getUpdateTag() {
        NBTTagCompound updateTag = this.writeToNBT(new NBTTagCompound());
        updateTag.setInteger(ACTION_STATE_NBT_KEY, this.actionState.ordinal());
        return updateTag;
    }

    @Override
    public SPacketUpdateTileEntity getUpdatePacket() {
        return new SPacketUpdateTileEntity(this.pos, 0, getUpdateTag());
    }

    @Override
    public void onDataPacket(NetworkManager net, SPacketUpdateTileEntity pkt) {
        this.handleUpdateTag(pkt.getNbtCompound());
    }

    @Override
    public void handleUpdateTag(NBTTagCompound tag) {
        super.handleUpdateTag(tag);
        EnumInfuserAction[] actionStates = EnumInfuserAction.values();
        int actionStateIndex = Math.clamp(tag.getInteger(ACTION_STATE_NBT_KEY), 0, actionStates.length - 1);
        this.actionState = actionStates[actionStateIndex];
    }

    @SideOnly(Side.CLIENT)
    public AxisAlignedBB getRenderBoundingBox() {
        // Use the tile entity specific methods here instead of World for consistency with other tile entities.
        Block blockType = this.getBlockType();
        int blockMeta = this.getBlockMetadata();
        // Only check this after we've gotten the block meta to avoid a data race.
        if (blockType instanceof BlockPhysicsInfuser blockPhysicsInfuser) {
            IBlockState blockState = blockType.getStateFromMeta(blockMeta);
            EnumFacing sideFacing = blockPhysicsInfuser.getDummyStateFacing(blockState);
            // First make the aabb for the main block, then include the dummy block, then include the bits coming out the top.
            return new AxisAlignedBB(this.getPos()).expand(-sideFacing.getXOffset(), -sideFacing.getYOffset(), -sideFacing.getZOffset())
                .expand(0, 0.3D, 0);
        }
        // Default in case broken.
        return super.getRenderBoundingBox();
    }

    public boolean isCurrentlyInShip() {
        Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(this.getWorld(), getPos());
        return physicsObject.isPresent();
    }

    @Override
    public void onButtonPress(int buttonId, EntityPlayer presser) {
        if (buttonId < 0 || buttonId >= EnumInfuserButton.values().length) {
            // Button not part of the gui, skip it.
            return;
        }
        EnumInfuserButton button = EnumInfuserButton.values()[buttonId];
        if (!button.buttonEnabled(this)) {
            // Correct an optimistic client if the server has newer tile or ship state.
            if (!this.getWorld().isRemote) this.sendUpdateToClients = true;
            return;
        }

        this.sendUpdateToClients = true;
        // TODO: Check if presser is allowed to press this button first
        /*
        if (presser isn't allowed) {
            return;
        }
         */
        switch (button) {
            case ASSEMBLE_SHIP -> {
                if (!this.isCurrentlyInShip()) {
                    // Create a ship
                    this.isTryingToAssembleShip = true;
                    setActionState(EnumInfuserAction.ASSEMBLING);
                }
                else {
                    // Destroy the ship if possible
                    this.isTryingToDisassembleShip = true;
                    this.disassembleCounter = 5;
                    setActionState(EnumInfuserAction.DISASSEMBLING);
                }
            }
            case ENABLE_PHYSICS -> this.isPhysicsEnabled = !this.isPhysicsEnabled();
            case ALIGN_SHIP -> this.isTryingToAlignShip = !this.isTryingToAlignShip();
        }

        if (this.getWorld().isRemote) {
            // If client, then send a packet telling the server we pressed this button.
            ValkyrienSkiesControl.controlGuiNetwork
                .sendToServer(new VSGuiButtonMessage(this, button.ordinal()));
        }
        else {
            Optional<PhysicsObject> optionalPhysicsObject = ValkyrienUtils.getPhysoManagingBlock(this.getWorld(), getPos());
            if (optionalPhysicsObject.isPresent()) {
                PhysicsObject physicsObject = optionalPhysicsObject.get();
                physicsObject.setPhysicsEnabled(this.isPhysicsEnabled);
                physicsObject.setShipAligningToGrid(this.isTryingToAlignShip);
                if (this.isTryingToDisassembleShip) {
                    physicsObject.setDeconstructState(PhysicsObject.DeconstructState.DECONSTRUCT_NORMAL);
                }
                else {
                    physicsObject.setDeconstructState(PhysicsObject.DeconstructState.NOT_DECONSTRUCTING);
                }
            }
        }
    }

    /**
     * @return If this TileEntity is in a ship then this returns whvalkyrium that ship can be
     * deconstructed or not, otherwise this returns true.
     */
    public boolean canShipBeDeconstructed() {
        Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(getWorld(), getPos());
        return physicsObject.isEmpty() || physicsObject.get().isShipAlignedToWorld();
    }

    @Override
    public boolean shouldRefresh(World world, BlockPos pos, IBlockState oldState, IBlockState newSate) {
        return oldState.getBlock() != newSate.getBlock();
    }

    /**
     * If the infuser is in a ship then we return true if this tile is the center of the ship. If the infuser isn't
     * in a ship then we just retard true regardless.
     *
     * @return
     */
    public boolean isCenterOfShip() {
        return true;
    }

    @SideOnly(Side.CLIENT)
    public double getCoreVerticalOffset(EnumInfuserCore enumInfuserCore, float partialTicks) {
        return (1 - partialTicks) * this.coreOffsetsPrevTick.get(enumInfuserCore) + partialTicks * this.coreOffsets.get(enumInfuserCore);
    }

    public boolean isTryingToAssembleShip() {
        return this.isTryingToAssembleShip;
    }

    public boolean isTryingToDisassembleShip() {
        return this.isTryingToDisassembleShip;
    }

    public EnumInfuserAction getActionState() {
        return this.actionState;
    }

    public boolean isActionInProgress() {
        return this.actionState != EnumInfuserAction.IDLE;
    }

    private void setActionState(EnumInfuserAction actionState) {
        if (this.actionState == actionState) return;
        this.actionState = actionState;
        this.sendUpdateToClients = true;
    }

    public boolean isPhysicsEnabled() {
        return this.isPhysicsEnabled;
    }

    public void setPhysicsEnabled(boolean physicsEnabled) {
        this.isPhysicsEnabled = physicsEnabled;
    }

    @SuppressWarnings("BooleanMethodIsAlwaysInverted")
    public boolean isTryingToAlignShip() {
        return this.isTryingToAlignShip;
    }

    @Nullable
    @Override
    public TileEntity createRelocatedTile(BlockPos newPos, @Nullable ShipData copiedBy) {
        NBTTagCompound tileEntNBT = writeToNBT(new NBTTagCompound());
        // Change the block position to be inside of the Ship
        tileEntNBT.setInteger("x", newPos.getX());
        tileEntNBT.setInteger("y", newPos.getY());
        tileEntNBT.setInteger("z", newPos.getZ());
        TileEntityPhysicsInfuser relocatedCopy = (TileEntityPhysicsInfuser) TileEntity.create(world, tileEntNBT);
        if (copiedBy == null) {
            // We've been moved from a ship into the world, reset the fields
            relocatedCopy.isTryingToAssembleShip = false;
            relocatedCopy.isTryingToDisassembleShip = false;
            relocatedCopy.isPhysicsEnabled = false;
            relocatedCopy.isTryingToAlignShip = false;
        }
        return relocatedCopy;
    }

    /**
     * An enum representation of the different core slots for the physics infuser.
     */
    public enum EnumInfuserCore {
        ONE("physics_core_small1_geo", 0, 45, 22),
        TWO("physics_core_small2_geo", 1, 45, 54),
        MAIN("physics_core_main_geo", 2, 79, 54),
        FOUR("physics_core_small4_geo", 3, 112, 54),
        FIVE("physics_core_small3_geo", 4, 112, 22);

        // The name of the model this core will render as in the PhysicsInfuserRenderer
        public final String coreModelName;
        // Container and gui values.
        public final int coreSlotIndex, guiXPos, guiYPos;

        EnumInfuserCore(String coreModelName, int coreSlotIndex, int guiXPos, int guiYPos) {
            this.coreModelName = coreModelName;
            this.coreSlotIndex = coreSlotIndex;
            this.guiXPos = guiXPos;
            this.guiYPos = guiYPos;
        }
    }

    public enum EnumInfuserAction {
        IDLE,
        ASSEMBLING,
        DISASSEMBLING
    }
}
