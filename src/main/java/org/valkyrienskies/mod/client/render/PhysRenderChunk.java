package org.valkyrienskies.mod.client.render;

import java.util.List;
import java.util.HashSet;
import java.util.Set;

import javax.annotation.Nullable;

import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.*;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.client.renderer.vertex.VertexBuffer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.BlockPos.MutableBlockPos;
import net.minecraft.world.ChunkCache;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import net.minecraftforge.client.ForgeHooksClient;
import net.minecraftforge.client.MinecraftForgeClient;
import net.minecraftforge.fml.common.Loader;
import org.lwjgl.opengl.GL11;
import org.valkyrienskies.mod.client.compat.FluidloggedRenderCompat;
import org.valkyrienskies.mod.client.compat.ShipTextureAnimationCompat;
import org.valkyrienskies.mod.common.util.TransformedAABB;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import valkyrienwarfare.api.TransformType;

public class PhysRenderChunk {
    public IVSRenderChunk[] renderChunks = new IVSRenderChunk[16];
    public PhysicsObject toRender;
    public Chunk chunk;
    private Set<TileEntity> tileEntitiesToRender;
    private boolean tileEntitiesDirty;
    private int lightingDirtySections;

    public PhysRenderChunk(PhysicsObject toRender, Chunk chunk) {
        this.toRender = toRender;
        this.chunk = chunk;
        for (int i = 0; i < 16; i++) {
            ExtendedBlockStorage storage = this.chunk.getBlockStorageArray()[i];
            if (storage != null) {
                IVSRenderChunk renderChunk;
                // Support old graphics cards that can't use VBOs.
                if (OpenGlHelper.useVbo()) {
                    renderChunk = new RenderLayerVBO(this.chunk, i * 16, i * 16 + 15, this);
                }
                else {
                    renderChunk = new RenderLayerDisplayList(this.chunk, i * 16, i * 16 + 15, this);
                }
                this.renderChunks[i] = renderChunk;
            }
        }
        this.tileEntitiesToRender = new HashSet<>(chunk.getTileEntityMap().values());
        if (!this.tileEntitiesToRender.isEmpty()) {
            Minecraft.getMinecraft().renderGlobal.updateTileEntities(List.of(), tileEntitiesToRender);
        }
    }

    public void renderBlockLayer(BlockRenderLayer layerToRender, double partialTicks, int pass, ICamera iCamera) {
        // Thousands of light callbacks can target the same section during one block change.
        // Consume each section once, without creating renderers for empty, light-only storage.
        int lightingUpdates = this.lightingDirtySections;
        this.lightingDirtySections = 0;
        for (int section = 0; section < renderChunks.length; section++) {
            if ((lightingUpdates & (1 << section)) != 0 && renderChunks[section] != null) {
                renderChunks[section].markDirty();
            }
        }

        if (this.tileEntitiesDirty) {
            Set<TileEntity> newTilesToRender = new HashSet<>(chunk.getTileEntityMap().values());
            if (!newTilesToRender.equals(this.tileEntitiesToRender)) {
                Set<TileEntity> removedTiles = new HashSet<>(this.tileEntitiesToRender);
                removedTiles.removeAll(newTilesToRender);
                Set<TileEntity> addedTiles = new HashSet<>(newTilesToRender);
                addedTiles.removeAll(this.tileEntitiesToRender);
                Minecraft.getMinecraft().renderGlobal.updateTileEntities(removedTiles, addedTiles);
            }
            this.tileEntitiesToRender = newTilesToRender;
            this.tileEntitiesDirty = false;
        }

        for (int i = 0; i < 16; i++) {
            IVSRenderChunk renderChunk = renderChunks[i];
            if (renderChunk != null) {
                AxisAlignedBB renderChunkBB = new AxisAlignedBB(chunk.x << 4, renderChunk.minY(), chunk.z << 4, (chunk.x << 4) + 16, renderChunk.minY() + 16, (chunk.z << 4) + 16);
                TransformedAABB polygon = new TransformedAABB(renderChunkBB, toRender.getShipTransformationManager().getRenderTransform(), TransformType.SUBSPACE_TO_GLOBAL);
                AxisAlignedBB inWorldBB = polygon.getEnclosedAABB();

                // Only render chunks that can be shown by the camera.
                if (iCamera.isBoundingBoxInFrustum(inWorldBB)) {
                    renderChunk.renderBlockLayer(layerToRender, partialTicks, pass);
                }
            }
        }
    }

    /**
     * A null layer mask queues lighting only; block updates also refresh tile entity membership.
     * */
    public void updateLayers(int minLayer, int maxLayer, @Nullable boolean[] layersToUpdate) {
        if (layersToUpdate != null) {
            this.tileEntitiesDirty = true;

            for (int layerY = minLayer; layerY <= maxLayer; layerY++) {
                IVSRenderChunk renderChunk = this.renderChunks[layerY];
                if (renderChunk != null) renderChunk.markDirty(layersToUpdate);
                else {
                    IVSRenderChunk renderLayer;
                    if (OpenGlHelper.useVbo()) {
                        renderLayer = new RenderLayerVBO(this.chunk, layerY * 16, layerY * 16 + 15, this);
                    }
                    else {
                        renderLayer = new RenderLayerDisplayList(this.chunk, layerY * 16, layerY * 16 + 15, this);
                    }
                    this.renderChunks[layerY] = renderLayer;
                }
            }
        }
        else {
            for (int layerY = minLayer; layerY <= maxLayer; layerY++) {
                this.lightingDirtySections |= 1 << layerY;
            }
        }
    }

    void killRenderChunk() {
        if (!this.tileEntitiesToRender.isEmpty()) {
            Minecraft.getMinecraft().renderGlobal.updateTileEntities(this.tileEntitiesToRender, List.of());
            this.tileEntitiesToRender.clear();
        }
        for (int i = 0; i < 16; i++) {
            IVSRenderChunk renderChunk = renderChunks[i];
            if (renderChunk != null) {
                renderChunk.deleteRenderChunk();
            }
        }
    }

    public interface IVSRenderChunk {
        void renderBlockLayer(BlockRenderLayer layerToRender, double partialTicks, int pass);

        void markDirty();

        void markDirty(boolean[] layersToUpdate);

        void deleteRenderChunk();

        int minY();

        int maxY();
    }

    private static class RenderLayerVBO implements IVSRenderChunk {
        private final ShipTextureAnimationCompat textureAnimation = new ShipTextureAnimationCompat(); //for celeritas and chibi compat
        Chunk chunkToRender;
        int yMin, yMax;
        VertexBuffer cutoutBuffer, cutoutMippedBuffer, solidBuffer, translucentBuffer;
        PhysRenderChunk parent;
        boolean needsCutoutUpdate, needsCutoutMippedUpdate, needsSolidUpdate, needsTranslucentUpdate;

        RenderLayerVBO(Chunk chunk, int yMin, int yMax, PhysRenderChunk parent) {
            chunkToRender = chunk;
            this.yMin = yMin;
            this.yMax = yMax;
            this.parent = parent;
            markDirty();
            cutoutBuffer = null;
            cutoutMippedBuffer = null;
            solidBuffer = null;
            translucentBuffer = null;
        }

        public int minY() {
            return yMin;
        }

        public int maxY() {
            return yMax;
        }

        public void markDirty() {
            needsCutoutUpdate = true;
            needsCutoutMippedUpdate = true;
            needsSolidUpdate = true;
            needsTranslucentUpdate = true;
        }

        @Override
        public void markDirty(boolean[] layersToUpdate) {
            for (BlockRenderLayer renderLayer : BlockRenderLayer.values()) {
                if (!layersToUpdate[renderLayer.ordinal()]) continue;

                switch (renderLayer) {
                    case CUTOUT -> needsCutoutUpdate = true;
                    case CUTOUT_MIPPED -> needsCutoutMippedUpdate = true;
                    case SOLID -> needsSolidUpdate = true;
                    case TRANSLUCENT -> needsTranslucentUpdate = true;
                }
            }
        }

        public void deleteRenderChunk() {
            if (this.cutoutBuffer != null) this.cutoutBuffer.deleteGlBuffers();
            if (this.cutoutMippedBuffer != null) this.cutoutMippedBuffer.deleteGlBuffers();
            if (this.solidBuffer != null) this.solidBuffer.deleteGlBuffers();
            if (this.translucentBuffer != null) this.translucentBuffer.deleteGlBuffers();

            this.textureAnimation.clear();
        }

        public void renderBlockLayer(BlockRenderLayer layerToRender, double partialTicks, int pass) {
            switch (layerToRender) {
                case CUTOUT:
                    if (this.needsCutoutUpdate) {
                        this.updateList(layerToRender);
                    }
                    FastBlockModelRenderer.renderVertexBuffer(this.cutoutBuffer);
                    break;
                case CUTOUT_MIPPED:
                    if (this.needsCutoutMippedUpdate) {
                        this.updateList(layerToRender);
                    }
                    FastBlockModelRenderer.renderVertexBuffer(this.cutoutMippedBuffer);
                    break;
                case SOLID:
                    if (this.needsSolidUpdate) {
                        this.updateList(layerToRender);
                    }
                    FastBlockModelRenderer.renderVertexBuffer(solidBuffer);
                    break;
                case TRANSLUCENT:
                    if (this.needsTranslucentUpdate) {
                        this.updateList(layerToRender);
                    }
                    FastBlockModelRenderer.renderVertexBuffer(translucentBuffer);
                    break;
                default:
                    break;
            }
            this.textureAnimation.markActive(layerToRender);
        }

        private void updateList(BlockRenderLayer layerToUpdate) {
            if (this.parent.toRender.getShipRenderer() == null) {
                return;
            }
            BlockPos offsetPos = this.parent.toRender.getShipRenderer().offsetPos;
            if (offsetPos == null) {
                return;
            }
            // Tessellator tessellator = Tessellator.getInstance();
            // BufferBuilder worldrenderer = tessellator.getBuffer();
            BufferBuilder vsChunkBuilder = FastBlockModelRenderer.VERTEX_BUILDER;
            vsChunkBuilder.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            vsChunkBuilder.setTranslation(-offsetPos.getX(), -offsetPos.getY(), -offsetPos.getZ());

            // The vertex buffer we're going to render this 16x16x16 chunk into
            VertexBuffer renderBuffer;

            switch (layerToUpdate) {
                case CUTOUT:
                    if (this.cutoutBuffer != null) {
                        this.cutoutBuffer.deleteGlBuffers();
                    }
                    this.cutoutBuffer = new VertexBuffer(DefaultVertexFormats.BLOCK);
                    renderBuffer = this.cutoutBuffer;
                    break;
                case CUTOUT_MIPPED:
                    if (this.cutoutMippedBuffer != null) {
                        this.cutoutMippedBuffer.deleteGlBuffers();
                    }
                    this.cutoutMippedBuffer = new VertexBuffer(DefaultVertexFormats.BLOCK);
                    renderBuffer = this.cutoutMippedBuffer;
                    break;
                case SOLID:
                    if (this.solidBuffer != null) {
                        this.solidBuffer.deleteGlBuffers();
                    }
                    this.solidBuffer = new VertexBuffer(DefaultVertexFormats.BLOCK);
                    renderBuffer = this.solidBuffer;
                    break;
                case TRANSLUCENT:
                    if (this.translucentBuffer != null) {
                        this.translucentBuffer.deleteGlBuffers();
                    }
                    this.translucentBuffer = new VertexBuffer(DefaultVertexFormats.BLOCK);
                    renderBuffer = this.translucentBuffer;
                    break;
                default:
                    throw new IllegalStateException("Unexpected value: " + layerToUpdate);
            }

            // Be careful with render layers
            BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();
            ForgeHooksClient.setRenderLayer(layerToUpdate);
            // Ship meshes already use local coordinates. Sample their own chunk lighting directly,
            // rather than running world-space ship lighting searches for every model vertex.
            ChunkCache renderAccess = new ChunkCache(
                    chunkToRender.getWorld(),
                    new BlockPos(chunkToRender.x << 4, yMin, chunkToRender.z << 4),
                    new BlockPos((chunkToRender.x << 4) + 15, yMax, (chunkToRender.z << 4) + 15),
                    1
            );
            MutableBlockPos pos = new MutableBlockPos();
            for (int x = chunkToRender.x * 16; x < chunkToRender.x * 16 + 16; x++) {
                for (int z = chunkToRender.z * 16; z < chunkToRender.z * 16 + 16; z++) {
                    for (int y = yMin; y <= yMax; y++) {
                        pos.setPos(x, y, z);
                        IBlockState iblockstate = chunkToRender.getBlockState(pos);
                        try {
                            if (Loader.isModLoaded("fluidlogged_api")) {
                                FluidloggedRenderCompat.renderFluidState(iblockstate, pos, renderAccess, vsChunkBuilder, layerToUpdate);
                            }
                            if (iblockstate.getBlock()
                                .canRenderInLayer(iblockstate, layerToUpdate)) {
                                Minecraft.getMinecraft().getBlockRendererDispatcher()
                                    .renderBlock(iblockstate, pos, renderAccess, vsChunkBuilder);
                            }
                        }
                        catch (NullPointerException e) {
                            System.out.println("Something was null!");
                        }
                    }
                }
            }

            this.textureAnimation.collect(layerToUpdate, vsChunkBuilder);
            vsChunkBuilder.finishDrawing();
            renderBuffer.bufferData(vsChunkBuilder.getByteBuffer());
            vsChunkBuilder.reset();

            // Fix the old render layer
            ForgeHooksClient.setRenderLayer(oldLayer);

            vsChunkBuilder.setTranslation(0, 0, 0);

            switch (layerToUpdate) {
                case CUTOUT:
                    needsCutoutUpdate = false;
                    break;
                case CUTOUT_MIPPED:
                    needsCutoutMippedUpdate = false;
                    break;
                case SOLID:
                    needsSolidUpdate = false;
                    break;
                case TRANSLUCENT:
                    needsTranslucentUpdate = false;
                    break;
                default:
                    break;
            }
        }
    }

    public static class RenderLayerDisplayList implements IVSRenderChunk {
        private final ShipTextureAnimationCompat textureAnimation = new ShipTextureAnimationCompat();

        Chunk chunkToRender;
        int yMin, yMax;
        int glCallListCutout, glCallListCutoutMipped, glCallListSolid, glCallListTranslucent;
        PhysRenderChunk parent;
        boolean needsCutoutUpdate, needsCutoutMippedUpdate, needsSolidUpdate, needsTranslucentUpdate;

        RenderLayerDisplayList(Chunk chunk, int yMin, int yMax, PhysRenderChunk parent) {
            chunkToRender = chunk;
            this.yMin = yMin;
            this.yMax = yMax;
            this.parent = parent;
            markDirty();
            glCallListCutout = GLAllocation.generateDisplayLists(4);
            glCallListCutoutMipped = glCallListCutout + 1;
            glCallListSolid = glCallListCutout + 2;
            glCallListTranslucent = glCallListCutout + 3;
        }

        public int minY() {
            return yMin;
        }

        public int maxY() {
            return yMax;
        }

        public void markDirty() {
            needsCutoutUpdate = true;
            needsCutoutMippedUpdate = true;
            needsSolidUpdate = true;
            needsTranslucentUpdate = true;
        }

        @Override
        public void markDirty(boolean[] layersToUpdate) {
            for (BlockRenderLayer renderLayer : BlockRenderLayer.values()) {
                if (!layersToUpdate[renderLayer.ordinal()]) continue;

                switch (renderLayer) {
                    case CUTOUT -> needsCutoutUpdate = true;
                    case CUTOUT_MIPPED -> needsCutoutMippedUpdate = true;
                    case SOLID -> needsSolidUpdate = true;
                    case TRANSLUCENT -> needsTranslucentUpdate = true;
                }
            }
        }

        public void deleteRenderChunk() {
            GLAllocation.deleteDisplayLists(this.glCallListCutout);
            GLAllocation.deleteDisplayLists(this.glCallListCutoutMipped);
            GLAllocation.deleteDisplayLists(this.glCallListSolid);
            GLAllocation.deleteDisplayLists(this.glCallListTranslucent);

            this.textureAnimation.clear();
        }

        public void renderBlockLayer(BlockRenderLayer layerToRender, double partialTicks, int pass) {
            switch (layerToRender) {
                case CUTOUT:
                    if (needsCutoutUpdate) {
                        updateList(layerToRender);
                    }
                    GL11.glCallList(glCallListCutout);
                    break;
                case CUTOUT_MIPPED:
                    if (needsCutoutMippedUpdate) {
                        updateList(layerToRender);
                    }
                    GL11.glCallList(glCallListCutoutMipped);
                    break;
                case SOLID:
                    if (needsSolidUpdate) {
                        updateList(layerToRender);
                    }
                    GL11.glCallList(glCallListSolid);
                    break;
                case TRANSLUCENT:
                    if (needsTranslucentUpdate) {
                        updateList(layerToRender);
                    }
                    GL11.glCallList(glCallListTranslucent);
                    break;
                default:
                    break;
            }
            this.textureAnimation.markActive(layerToRender);
        }

        private void updateList(BlockRenderLayer layerToUpdate) {
            if (parent.toRender.getShipRenderer() == null) {
                return;
            }
            BlockPos offsetPos = parent.toRender.getShipRenderer().offsetPos;
            if (offsetPos == null) {
                return;
            }
            Tessellator tessellator = Tessellator.getInstance();
            BufferBuilder worldrenderer = tessellator.getBuffer();
            worldrenderer.begin(7, DefaultVertexFormats.BLOCK);
            worldrenderer.setTranslation(-offsetPos.getX(), -offsetPos.getY(), -offsetPos.getZ());
            GL11.glPushMatrix();
            switch (layerToUpdate) {
                case CUTOUT:
                    GLAllocation.deleteDisplayLists(glCallListCutout);
                    GL11.glNewList(glCallListCutout, GL11.GL_COMPILE);
                    break;
                case CUTOUT_MIPPED:
                    GLAllocation.deleteDisplayLists(glCallListCutoutMipped);
                    GL11.glNewList(glCallListCutoutMipped, GL11.GL_COMPILE);
                    break;
                case SOLID:
                    GLAllocation.deleteDisplayLists(glCallListSolid);
                    GL11.glNewList(glCallListSolid, GL11.GL_COMPILE);
                    break;
                case TRANSLUCENT:
                    GLAllocation.deleteDisplayLists(glCallListTranslucent);
                    GL11.glNewList(glCallListTranslucent, GL11.GL_COMPILE);
                    break;
                default:
                    break;
            }

            GlStateManager.pushMatrix();
            // worldrenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
            IBlockState iblockstate;

            BlockRenderLayer oldLayer = MinecraftForgeClient.getRenderLayer();
            ForgeHooksClient.setRenderLayer(layerToUpdate);
            ChunkCache renderAccess = new ChunkCache(
                    this.chunkToRender.getWorld(),
                    new BlockPos(this.chunkToRender.x << 4, this.yMin, this.chunkToRender.z << 4),
                    new BlockPos((this.chunkToRender.x << 4) + 15, this.yMax, (this.chunkToRender.z << 4) + 15),
                    1
            );
            MutableBlockPos pos = new MutableBlockPos();
            for (int x = this.chunkToRender.x * 16; x < this.chunkToRender.x * 16 + 16; x++) {
                for (int z = this.chunkToRender.z * 16; z < this.chunkToRender.z * 16 + 16; z++) {
                    for (int y = this.yMin; y <= this.yMax; y++) {
                        pos.setPos(x, y, z);
                        iblockstate = this.chunkToRender.getBlockState(pos);
                        try {
                            if (Loader.isModLoaded("fluidlogged_api")) {
                                FluidloggedRenderCompat.renderFluidState(iblockstate, pos, renderAccess, worldrenderer, layerToUpdate);
                            }
                            if (iblockstate.getBlock().canRenderInLayer(iblockstate, layerToUpdate)) {
                                Minecraft.getMinecraft().getBlockRendererDispatcher()
                                        .renderBlock(iblockstate, pos, renderAccess, worldrenderer);
                            }
                        }
                        catch (NullPointerException e) {
                            System.out.println("Something was null! LValkyrienSkiesBase/render/PhysRenderChunk#updateList");
                        }
                    }
                }
            }
            this.textureAnimation.collect(layerToUpdate, worldrenderer);
            tessellator.draw();
            // worldrenderer.finishDrawing();
            ForgeHooksClient.setRenderLayer(oldLayer);
            GlStateManager.popMatrix();
            GL11.glEndList();
            GL11.glPopMatrix();
            worldrenderer.setTranslation(0, 0, 0);

            switch (layerToUpdate) {
                case CUTOUT:
                    needsCutoutUpdate = false;
                    break;
                case CUTOUT_MIPPED:
                    needsCutoutMippedUpdate = false;
                    break;
                case SOLID:
                    needsSolidUpdate = false;
                    break;
                case TRANSLUCENT:
                    needsTranslucentUpdate = false;
                    break;
                default:
                    break;
            }
        }
    }
}
