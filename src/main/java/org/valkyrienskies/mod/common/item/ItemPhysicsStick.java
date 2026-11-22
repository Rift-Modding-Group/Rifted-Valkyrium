package org.valkyrienskies.mod.common.item;

import net.minecraft.client.resources.I18n;
import net.minecraft.client.util.ITooltipFlag;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ActionResult;
import net.minecraft.util.EnumActionResult;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.EnumHand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.text.TextComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsStickController;
import org.valkyrienskies.mod.common.util.BaseItem;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import valkyrienwarfare.api.TransformType;

import javax.annotation.ParametersAreNonnullByDefault;
import java.util.List;

@ParametersAreNonnullByDefault
public class ItemPhysicsStick extends BaseItem {
    public static String SELECTION_TAG = "PhysicsSelection";
    public static final double REACH = 16D;

    public ItemPhysicsStick() {
        super("physics_stick", false);
        this.setCreativeTab(ValkyrienSkiesMod.RV_DEBUG_TAG);
        this.setMaxStackSize(1);
    }

    @Override
    @NotNull
    public EnumActionResult onItemUseFirst(
            EntityPlayer player, World world, BlockPos pos, EnumFacing facing,
            float hitX, float hitY, float hitZ, EnumHand hand
    ) {
        return this.onItemRightClick(world, player, hand).getType();
    }

    @Override
    @NotNull
    public ActionResult<ItemStack> onItemRightClick(World world, EntityPlayer player, EnumHand hand) {
        ItemStack stack = player.getHeldItem(hand);
        if (!world.isRemote) {
            NBTTagCompound tag = stack.getTagCompound();
            if (tag == null) tag = new NBTTagCompound();

            tag.setBoolean("PhysicsAction", true);
            tag.setBoolean("PhysicsSneaking", player.isSneaking());
            stack.setTagCompound(tag);
        }
        return new ActionResult<>(EnumActionResult.SUCCESS, stack);
    }

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int itemSlot, boolean isSelected) {
        if (world.isRemote || !(entity instanceof EntityPlayer player)) return;
        NBTTagCompound tag = stack.getTagCompound();
        if (tag == null) return;
        if (!player.isEntityAlive() || player.isSpectator() || (player.getHeldItemMainhand() != stack && player.getHeldItemOffhand() != stack)) {
            tag.removeTag(SELECTION_TAG);
            tag.removeTag("PhysicsAction");
            tag.removeTag("PhysicsSneaking");
            return;
        }
        if (!tag.getBoolean("PhysicsAction")) return;
        boolean sneaking = tag.getBoolean("PhysicsSneaking");
        tag.removeTag("PhysicsAction");
        tag.removeTag("PhysicsSneaking");

        NBTTagCompound selection = tag.getCompoundTag(SELECTION_TAG);
        if (selection.hasUniqueId("Ship") && selection.getInteger("Dimension") == world.provider.getDimension()) {
            PhysicsObject selected = ValkyrienUtils.getServerShipManager(world).getPhysObjectFromUUID(selection.getUniqueId("Ship"));
            if (selected != null && selected.physicsStickController != null && selected.physicsStickController.stack == stack) {
                PhysicsStickController.release(selected);
                if (!sneaking) {
                    player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.physics_stick.released"), true);
                    return;
                }
            }
        }
        tag.removeTag(SELECTION_TAG);

        Vec3d eyes = player.getPositionEyes(1F);
        Vec3d end = eyes.add(player.getLookVec().scale(REACH));
        RayTraceResult hit = world.rayTraceBlocks(eyes, end, false, false, false);
        PhysicsObject ship = null;
        if (hit != null && hit.typeOfHit == RayTraceResult.Type.BLOCK && isWithinReach(eyes, hit.hitVec)) {
            ship = ValkyrienUtils.getPhysoManagingBlock(world, hit.getBlockPos()).orElse(null);
        }
        if (ship == null) {
            player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.physics_stick.select_ship"), true);
            return;
        }
        if (sneaking) {
            PhysicsStickController.release(ship);
            boolean enabled = !ship.isPhysicsEnabled();
            ship.setPhysicsEnabled(enabled);
            String message;
            if (enabled) message = "message.valkyrienskies.physics_stick.enabled";
            else message = "message.valkyrienskies.physics_stick.disabled";
            player.sendStatusMessage(new TextComponentTranslation(message), true);
            return;
        }
        if (ship.getShipData().entityAttachment != null || ship.isDeconstructing() || ship.physicsStickController != null) {
            player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.physics_stick.unavailable"), true);
            return;
        }

        Vec3d grabPoint = ship.getShipTransform().transform(hit.hitVec, TransformType.GLOBAL_TO_SUBSPACE);
        PhysicsStickController controller = new PhysicsStickController(
                player.getUniqueID(), stack, grabPoint,
                ship.getShipTransform().rotationQuaternion(TransformType.SUBSPACE_TO_GLOBAL),
                hit.hitVec, player.rotationYaw, ship.isPhysicsEnabled(), eyes.distanceTo(hit.hitVec)
        );
        ship.physicsStickController = controller;
        ship.addForceInducer(controller);
        ship.setShipAligningToGrid(false);
        ship.setPhysicsEnabled(true);
        selection = new NBTTagCompound();
        selection.setUniqueId("Ship", ship.getUuid());
        selection.setInteger("Dimension", world.provider.getDimension());
        tag.setTag(SELECTION_TAG, selection);
        player.sendStatusMessage(new TextComponentTranslation("message.valkyrienskies.physics_stick.selected"), true);
    }

    public static boolean isWithinReach(Vec3d eyes, Vec3d point) {
        return eyes.squareDistanceTo(point) <= REACH * REACH;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, @Nullable World world, List<String> tooltip, ITooltipFlag flag) {
        tooltip.add(I18n.format("tooltip.valkyrienskies.physics_stick.usage"));
    }
}
