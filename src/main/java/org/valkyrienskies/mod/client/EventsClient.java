package org.valkyrienskies.mod.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.ISound;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderGlobal;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.RayTraceResult;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.ModelBakeEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.client.event.sound.PlaySoundEvent;
import net.minecraftforge.event.world.ChunkEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent.Phase;
import org.joml.Vector3d;
import org.joml.Vector3dc;
import org.lwjgl.opengl.GL11;
import org.valkyrienskies.mod.client.render.GibsModelRegistry;
import org.valkyrienskies.mod.common.capability.VSCapabilityRegistry;
import org.valkyrienskies.mod.common.capability.entity_ship_draggable.IEntityShipDraggable;
import org.valkyrienskies.mod.common.capability.ship_world.IShipWorld;
import org.valkyrienskies.mod.common.ships.QueryableShipData;
import org.valkyrienskies.mod.common.ships.ShipData;
import org.valkyrienskies.mod.common.ships.entity_interaction.EntityDraggable;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.tileentity.TileEntityWaterPump;
import org.valkyrienskies.mod.common.util.VSRenderUtils;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;
import org.valkyrienskies.mod.fixes.SoundFixWrapper;
import org.valkyrienskies.api.TransformType;

import java.util.Optional;

public class EventsClient {
    private static double oldXOff;
    private static double oldYOff;
    private static double oldZOff;

    @SubscribeEvent
    public void onClientTick(ClientTickEvent event) {
        final World world = Minecraft.getMinecraft().world;
        // There's no world, so there's nothing to run.
        if (world == null) return;

        IShipWorld shipWorld = world.getCapability(VSCapabilityRegistry.VS_SHIP_WORLD, null);
        if (shipWorld == null) return;

        // Pretend this is the world tick
        if (event.phase == Phase.START) {
            for (PhysicsObject wrapper : shipWorld.getManager().getAllLoadedPhysObj()) {
                // This is necessary because Minecraft will run a raytrace right after this
                // event to determine what the player is looking at for interaction purposes.
                // That raytrace will use the render transform, so we must have the render
                // transform set to a partialTick of 1.0.
                wrapper.getShipTransformationManager().updateRenderTransform(1.0);
            }

            // Reset the air pocket status of all entities
            for (final Entity entity : world.loadedEntityList) {
                IEntityShipDraggable draggable = entity.getCapability(VSCapabilityRegistry.VS_ENTITY_SHIP_DRAGGABLE, null);
                if (draggable == null) continue;
                draggable.decrementTicksAirPocket();
            }
        }
        else if (event.phase == Phase.END) {
            if (!Minecraft.getMinecraft().isGamePaused()) {
                // Tick the IShipManager on the world client.
                shipWorld.getManager().tick();
                EntityDraggable.tickAddedVelocityForWorld(world);
            }
        }
    }

    @SubscribeEvent
    public void onPlaySoundEvent(PlaySoundEvent event) {
        if (Minecraft.getMinecraft().world != null) {
            ISound sound = event.getSound();
            BlockPos pos = new BlockPos(sound.getXPosF(), sound.getYPosF(), sound.getZPosF());

            Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(Minecraft.getMinecraft().world, pos);
            if (physicsObject.isPresent()) {
                Vector3d newSoundLocation = new Vector3d(sound.getXPosF(), sound.getYPosF(), sound.getZPosF());
                physicsObject.get()
                    .getShipTransformationManager()
                    .getCurrentTickTransform()
                    .transformPosition(newSoundLocation, TransformType.SUBSPACE_TO_GLOBAL);

                SoundFixWrapper soundFix = new SoundFixWrapper(sound, newSoundLocation);

                event.setResultSound(soundFix);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST, receiveCanceled = true)
    public void onDrawBlockHighlightEventFirst(DrawBlockHighlightEvent event) {
        GL11.glPushMatrix();
        BlockPos pos = Minecraft.getMinecraft().objectMouseOver.getBlockPos();
        Optional<PhysicsObject> physicsObject = ValkyrienUtils
            .getPhysoManagingBlock(Minecraft.getMinecraft().world, pos);
        if (physicsObject.isPresent()) {
            RayTraceResult objectOver = Minecraft.getMinecraft().objectMouseOver;
            if (objectOver != null && objectOver.hitVec != null) {
                BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                oldXOff = buffer.xOffset;
                oldYOff = buffer.yOffset;
                oldZOff = buffer.zOffset;

                buffer.setTranslation(-physicsObject.get()
                    .getShipRenderer().offsetPos.getX(), -physicsObject.get()
                    .getShipRenderer().offsetPos.getY(), -physicsObject.get()
                    .getShipRenderer().offsetPos.getZ());
                physicsObject.get()
                    .getShipRenderer()
                    .applyRenderTransform(event.getPartialTicks());
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onDrawBlockHighlightEventLast(DrawBlockHighlightEvent event) {
        BlockPos pos = Minecraft.getMinecraft().objectMouseOver.getBlockPos();
        Optional<PhysicsObject> physicsObject = ValkyrienUtils
            .getPhysoManagingBlock(Minecraft.getMinecraft().world, pos);
        if (physicsObject.isPresent()) {
            RayTraceResult objectOver = Minecraft.getMinecraft().objectMouseOver;
            if (objectOver != null && objectOver.hitVec != null) {
                BufferBuilder buffer = Tessellator.getInstance().getBuffer();
                buffer.xOffset = oldXOff;
                buffer.yOffset = oldYOff;
                buffer.zOffset = oldZOff;
                // wrapper.wrapping.renderer.inverseTransform(event.getPartialTicks());
                // objectOver.hitVec = RotationMatrices.applyTransform(wrapper.wrapping.coordTransform.lToWTransform, objectOver.hitVec);
            }
        }
        GL11.glPopMatrix();
    }

    /**
     * Register textures for all the models registered in the GibsModelRegistry.
     */
    @SubscribeEvent
    public void onTextureStitchEvent(TextureStitchEvent.Pre event) {
        GibsModelRegistry.registerTextures(event);
    }

    @SubscribeEvent
    public void onModelBake(ModelBakeEvent event) {
        GibsModelRegistry.onModelBakeEvent(event);
    }

    /**
     * Renders temporary water pump ranges and ship debug bounding boxes.
     */
    @SubscribeEvent
    public void onRenderWorldLastEvent(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        World world = Minecraft.getMinecraft().world;
        if (mc.getRenderViewEntity() == null || world == null) return;

        float partialTicks = event.getPartialTicks();
        Vector3dc offset = VSRenderUtils.getEntityPartialPosition(mc.getRenderViewEntity(), partialTicks).negate();

        this.renderWaterPumpRanges(world, offset, partialTicks);

        //---debug only stuff here---
        if (!mc.getRenderManager().isDebugBoundingBox() || mc.isReducedDebug()) return;
        for (PhysicsObject physo : ValkyrienUtils.getPhysosLoadedInWorld(world)) {
            physo.getShipRenderer().renderDebugInfo(offset);
        }
    }

    private void renderWaterPumpRanges(World world, Vector3dc offset, float partialTicks) {
        boolean renderStateEnabled = false;

        for (TileEntity tileEntity : world.loadedTileEntityList) {
            if (!(tileEntity instanceof TileEntityWaterPump waterPump) || waterPump.getRangeVisualizationTicks() <= 0) {
                continue;
            }

            //begin rendering
            if (!renderStateEnabled) {
                GlStateManager.enableBlend();
                GlStateManager.tryBlendFuncSeparate(
                        GlStateManager.SourceFactor.SRC_ALPHA,
                        GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                        GlStateManager.SourceFactor.ONE,
                        GlStateManager.DestFactor.ZERO
                );
                GlStateManager.disableTexture2D();
                GlStateManager.disableLighting();
                GlStateManager.disableCull();
                GlStateManager.disableDepth();
                GlStateManager.depthMask(false);

                renderStateEnabled = true;
            }

            GlStateManager.pushMatrix();
            Optional<PhysicsObject> physicsObject = ValkyrienUtils.getPhysoManagingBlock(world, waterPump.getPos());
            if (physicsObject.isPresent()) {
                PhysicsObject ship = physicsObject.get();
                ship.getShipRenderer().applyRenderTransform(partialTicks);
                BlockPos renderOffset = ship.getShipRenderer().offsetPos;
                GlStateManager.translate(
                        waterPump.getPos().getX() - renderOffset.getX(),
                        waterPump.getPos().getY() - renderOffset.getY(),
                        waterPump.getPos().getZ() - renderOffset.getZ()
                );
            }
            else {
                GlStateManager.translate(
                        waterPump.getPos().getX() + offset.x(),
                        waterPump.getPos().getY() + offset.y(),
                        waterPump.getPos().getZ() + offset.z()
                );
            }

            //render water pump range
            float fade = Math.clamp((waterPump.getRangeVisualizationTicks() - partialTicks) / 20f, 0f, 1f);
            BlockPos pumpPos = waterPump.getPos();
            AxisAlignedBB rangeBox = waterPump.getPumpRangeBB()
                    .offset(-pumpPos.getX(), -pumpPos.getY(), -pumpPos.getZ())
                    .grow(0.002D);

            GlStateManager.glLineWidth(2f);
            RenderGlobal.drawSelectionBoundingBox(rangeBox, 1f, 1f, 0f, 0.8f * fade);
            GlStateManager.glLineWidth(1f);
            GlStateManager.popMatrix();
        }

        //end bound rendering
        if (renderStateEnabled) {
            GlStateManager.depthMask(true);
            GlStateManager.enableDepth();
            GlStateManager.enableCull();
            GlStateManager.enableLighting();
            GlStateManager.enableTexture2D();
            GlStateManager.disableBlend();
            GlStateManager.resetColor();
        }
    }

    /**
     * Used to handle when a chunk in a ship gets updated. This allows us to create ships on the client without
     * requiring all the chunks are present at the time of ship creation.
     */
    @SubscribeEvent
    public void onChunkLoadEvent(ChunkEvent.Load event) {
        if (!event.getWorld().isRemote) return;
        Chunk chunk = event.getChunk();
        QueryableShipData queryableShipData = QueryableShipData.get(event.getWorld());
        Optional<ShipData> shipClaimingOptional = queryableShipData.getShipFromChunk(chunk.x, chunk.z);
        if (shipClaimingOptional.isPresent()) {
            ShipData shipData = shipClaimingOptional.get();
            IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(event.getWorld());
            if (physObjectWorld == null) {
                throw new IllegalStateException("Could not get ship manager from world!");
            }
            PhysicsObject physicsObject = physObjectWorld.getPhysObjectFromUUID(shipData.getUuid());
            if (physicsObject != null) physicsObject.updateChunk(chunk);
        }
    }
}
