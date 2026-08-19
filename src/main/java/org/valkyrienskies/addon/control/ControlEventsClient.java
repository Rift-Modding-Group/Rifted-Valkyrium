package org.valkyrienskies.addon.control;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.block.model.IBakedModel;
import net.minecraft.client.renderer.block.model.ModelResourceLocation;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.EnumHand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraftforge.client.event.InputUpdateEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.model.IModel;
import net.minecraftforge.client.model.ModelLoader;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;
import org.valkyrienskies.addon.control.capability.controlNodeUser.ICapabilityControlNodeUser;
import org.valkyrienskies.addon.control.capability.lastRelay.ICapabilityLastRelay;
import org.valkyrienskies.addon.control.item.ItemBaseWire;
import org.valkyrienskies.addon.control.network.VSStoppedUsingControlNodeMessage;
import org.valkyrienskies.addon.control.renderer.infuser_core_rendering.InfuserCoreBakedModel;
import org.valkyrienskies.addon.control.tileentity.ITileEntityControlNode;
import org.valkyrienskies.mod.client.VSKeyHandler;

public class ControlEventsClient {
    private boolean suppressDismountUntilRelease = false;

    @SubscribeEvent
    public void render(RenderGameOverlayEvent.Post event) {
        Minecraft minecraft = Minecraft.getMinecraft();
        EntityPlayer player = Minecraft.getMinecraft().player;
        FontRenderer fontRenderer = minecraft.fontRenderer;

        if (fontRenderer == null || player == null || event.getType() != RenderGameOverlayEvent.ElementType.TEXT) return;

        ScaledResolution scaledresolution = new ScaledResolution(Minecraft.getMinecraft());

        //---render pilot text---
        ICapabilityControlNodeUser nodeUser = Minecraft.getMinecraft().player.getCapability(ValkyrienSkiesControl.controlNodeUserCapability, null);
        if (nodeUser != null && nodeUser.getUsedControlNodePos() != null) {
            TileEntity pilotedTile = player.getEntityWorld().getTileEntity(nodeUser.getUsedControlNodePos());
            if (pilotedTile instanceof ITileEntityControlNode usedControlNode) {
                usedControlNode.renderPilotText(fontRenderer, scaledresolution);
            }
        }

        //---render wire connection text---
        ItemStack mainHandStack = player.getHeldItem(EnumHand.MAIN_HAND);
        ItemStack offHandStack = player.getHeldItem(EnumHand.OFF_HAND);

        if (mainHandStack.getItem() instanceof ItemBaseWire) {
            this.renderWireConnectivityText(mainHandStack, fontRenderer, scaledresolution);
        }
        else if (offHandStack.getItem() instanceof ItemBaseWire && player.isSneaking()) {
            this.renderWireConnectivityText(offHandStack, fontRenderer, scaledresolution);
        }
    }

    //helper method for rendering text involving held wire
    private void renderWireConnectivityText(ItemStack wireItemStack, FontRenderer fontRenderer, ScaledResolution scaledresolution) {
        if (!(wireItemStack.getItem() instanceof ItemBaseWire)) return;

        ICapabilityLastRelay inst = wireItemStack.getCapability(ValkyrienSkiesControl.lastRelayCapability, null);
        if (inst == null || inst.getLastRelay() == null) return;

        // White text.
        int color = 0xFFFFFF;
        // Extra spaces so the that the text is closer to the middle when rendered.
        String message = "Currently creating connection...";
        int i = scaledresolution.getScaledWidth();
        int height = scaledresolution.getScaledHeight() - 35;
        float middle = (float) (i / 2 - fontRenderer.getStringWidth(message) / 2);
        fontRenderer.drawStringWithShadow(message, middle, height, color);
    }

    /**
     * Force the game to load the inventory texture for physics core.
     */
    @SubscribeEvent
    public void onTextureStitchPre(TextureStitchEvent.Pre event) {
        ResourceLocation mainCoreInventoryTexture = new ResourceLocation(ValkyrienSkiesControl.MOD_ID,
                "items/main_core");
        ResourceLocation smallCoreInventoryTexture = new ResourceLocation(ValkyrienSkiesControl.MOD_ID,
                "items/small_core");
        event.getMap()
                .registerSprite(mainCoreInventoryTexture);
        event.getMap()
                .registerSprite(smallCoreInventoryTexture);
    }

    /**
     * Replace the item model of the physics core with the custom behavior one.
     */
    @SubscribeEvent
    public void onModelBake(ModelBakeEvent event) {
        ResourceLocation modelResourceLocation = new ResourceLocation(ValkyrienSkiesControl.MOD_ID,
                "item/infuser_core_main");
        try {
            IModel model = ModelLoaderRegistry.getModel(modelResourceLocation);
            IBakedModel inventoryModel = model
                    .bake(model.getDefaultState(), DefaultVertexFormats.ITEM,
                            ModelLoader.defaultTextureGetter());
            IBakedModel handModel = event.getModelRegistry()
                    .getObject(new ModelResourceLocation(
                            ValkyrienSkiesControl.MOD_ID + ":" + ValkyrienSkiesControl.INSTANCE.physicsCore
                                    .getTranslationKey()
                                    .substring(5), "inventory"));

            event.getModelRegistry()
                    .putObject(
                            new ModelResourceLocation(ValkyrienSkiesControl.MOD_ID + ":testmodel", "inventory"),
                            new InfuserCoreBakedModel(handModel, inventoryModel));
        }
        catch (Exception e) {
            e.printStackTrace();
        }

    }

    /**
     * this exists to ensure that player cannot dismount whatever chair entity they're on
     * when using shift to stop controlling any control node
     * */
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.side == Side.SERVER) return;
        if (event.phase != TickEvent.Phase.START) return;

        ICapabilityControlNodeUser controlNodeUser = event.player.getCapability(ValkyrienSkiesControl.controlNodeUserCapability, null);
        if (controlNodeUser == null) return;

        boolean dismountKeyDown = VSKeyHandler.dismountKey.isKeyDown();

        //a new dismount can only happen after shift has been released.
        if (!dismountKeyDown) this.suppressDismountUntilRelease = false;

        //enable supress flag when theres conflict
        if (dismountKeyDown && controlNodeUser.getUsedControlNodePos() != null
            && event.player.isRiding() && VSKeyHandler.dismountKey.conflicts(Minecraft.getMinecraft().gameSettings.keyBindSneak)
        ) {
            this.suppressDismountUntilRelease = true;
        }
    }

    //apply dismount supression
    @SideOnly(Side.CLIENT)
    @SubscribeEvent
    public void onInputUpdate(InputUpdateEvent event) {
        if (event.getEntityPlayer() != Minecraft.getMinecraft().player) return;

        if (this.suppressDismountUntilRelease) event.getMovementInput().sneak = false;
    }
}
