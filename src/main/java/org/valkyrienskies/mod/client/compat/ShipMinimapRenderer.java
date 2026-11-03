package org.valkyrienskies.mod.client.compat;

import net.minecraft.block.material.MapColor;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.storage.ExtendedBlockStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.valkyrienskies.mod.common.config.VSConfig;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Renderer for ships on the minimap mods
 * */
public class ShipMinimapRenderer {
    @NotNull
    public static final ShipMinimapRenderer INSTANCE = new ShipMinimapRenderer();

    @NotNull
    private final Map<UUID, ShipImage> images = new HashMap<>();
    @Nullable
    private World world;

    private ShipMinimapRenderer() {}

    /**
     * Refreshes ship images and releases stale textures on the client tick.
     * */
    public void tick() {
        Minecraft mc = Minecraft.getMinecraft();
        World currentWorld = mc.world;
        if (this.world != currentWorld || !VSConfig.showShipInMinimapMods) {
            this.clear();
            this.world = currentWorld;
        }
        if (currentWorld == null || !VSConfig.showShipInMinimapMods) return;

        Set<UUID> loadedShips = new HashSet<>();
        List<PhysicsObject> ships = new ArrayList<>();
        for (PhysicsObject ship : ValkyrienUtils.getPhysosLoadedInWorld(currentWorld)) {
            ships.add(ship);
            loadedShips.add(ship.getShipData().getUuid());
        }
        Iterator<Map.Entry<UUID, ShipImage>> iterator = this.images.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<UUID, ShipImage> entry = iterator.next();
            if (loadedShips.contains(entry.getKey())) continue;
            entry.getValue().texture.deleteGlTexture();
            iterator.remove();
        }
        if (ships.isEmpty() || mc.isGamePaused()) return;

        int boundTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        long tick = mc.world.getTotalWorldTime();

        for (PhysicsObject ship : ships) {
            ShipTransform transform = ship.getShipTransformationManager().getCurrentTickTransform();
            UUID id = ship.getShipData().getUuid();
            ShipImage image = this.images.get(id);
            if (image == null) {
                image = new ShipImage();
                this.images.put(id, image);
            }
            ShipMapRaster.Image previousRaster = image.raster;
            BlockPos reference = ship.getReferenceBlockPos();
            boolean scanBlocks = image.raster == null || tick < image.updatedAt || tick - image.updatedAt >= 5;
            boolean blocksChanged = false;
            if (scanBlocks) {
                List<ShipMapRaster.MapBlock> blocks = new ArrayList<>();
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                BlockPos.MutableBlockPos neighbor = new BlockPos.MutableBlockPos();
                EnumFacing[] faces = EnumFacing.values();
                for (Chunk chunk : ship.getClaimedChunkCache()) {
                    if (chunk == null || chunk.isEmpty()) continue;
                    for (ExtendedBlockStorage section : chunk.getBlockStorageArray()) {
                        if (section == null || section.isEmpty()) continue;
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                for (int x = 0; x < 16; x++) {
                                    IBlockState state = section.get(x, y, z);
                                    pos.setPos((chunk.x << 4) + x, section.getYLocation() + y, (chunk.z << 4) + z);
                                    if (state.getBlock().isAir(state, mc.world, pos)) continue;
                                    MapColor mapColor = state.getMapColor(mc.world, pos);
                                    if (mapColor == MapColor.AIR) continue;

                                    boolean buried = state.isOpaqueCube();
                                    if (buried) {
                                        for (EnumFacing face : faces) {
                                            neighbor.setPos(pos).move(face);
                                            if (!ship.getChunkClaim().containsChunk(neighbor.getX() >> 4, neighbor.getZ() >> 4)
                                                    || !mc.world.getBlockState(neighbor).isOpaqueCube()
                                            ) {
                                                buried = false;
                                                break;
                                            }
                                        }
                                    }
                                    if (buried) continue;
                                    AxisAlignedBB box = state.getBoundingBox(mc.world, pos);
                                    if (box == null) continue;
                                    blocks.add(new ShipMapRaster.MapBlock(
                                            box.offset(
                                                    pos.getX() - reference.getX(),
                                                    pos.getY() - reference.getY(),
                                                    pos.getZ() - reference.getZ()
                                            ),
                                            mapColor.colorValue
                                    ));
                                }
                            }
                        }
                    }
                }
                blocksChanged = !blocks.equals(image.blocks);
                if (blocksChanged) image.blocks = blocks;
                image.updatedAt = tick;
            }

            boolean projectBlocks = image.raster == null || blocksChanged || ShipMapRaster.needsProjection(image.transform, transform);
            if (projectBlocks) {
                Matrix4d projection = new Matrix4d().translate(-transform.getPosX(), -transform.getPosY(), -transform.getPosZ())
                        .mul(transform.getSubspaceToGlobal()).translate(reference.getX(), reference.getY(), reference.getZ());
                image.raster = ShipMapRaster.rasterize(image.blocks, projection, 512);
                image.transform = transform;
            }
            if (!scanBlocks && !projectBlocks) continue;

            ShipMapRaster.Image raster = image.raster;
            boolean textureChanged = image.texture == null || previousRaster == null
                    || previousRaster.width() != raster.width() || previousRaster.height() != raster.height();
            if (textureChanged) {
                if (image.texture != null) image.texture.deleteGlTexture();
                image.texture = new DynamicTexture(raster.width(), raster.height());
            }
            int[] textureData = image.texture.getTextureData();
            Matrix4d snapshotToCurrent = new Matrix4d(transform.getSubspaceToGlobal()).mul(image.transform.getGlobalToSubspace());
            Vector3d worldPoint = new Vector3d();
            // Keep the unmasked raster so movement can reveal pixels previously hidden by terrain.
            for (int z = 0; z < raster.height(); z++) {
                for (int x = 0; x < raster.width(); x++) {
                    int index = z * raster.width() + x;
                    int color = raster.colors()[index];
                    if (color != 0) {
                        worldPoint.set(image.transform.getPosX() + raster.minX() + (x + 0.5D) * raster.pixelSize(),
                                image.transform.getPosY() + raster.heights()[index],
                                image.transform.getPosZ() + raster.minZ() + (z + 0.5D) * raster.pixelSize());
                        snapshotToCurrent.transformPosition(worldPoint);
                        int worldX = (int)Math.floor(worldPoint.x);
                        int worldZ = (int)Math.floor(worldPoint.z);
                        Chunk terrain = mc.world.getChunkProvider().getLoadedChunk(worldX >> 4, worldZ >> 4);
                        if (terrain != null && terrain.getHeightValue(worldX & 15, worldZ & 15) > worldPoint.y + 1D) color = 0;
                    }
                    if (textureData[index] != color) {
                        textureData[index] = color;
                        textureChanged = true;
                    }
                }
            }
            if (textureChanged) image.texture.updateDynamicTexture();
        }
        GlStateManager.bindTexture(boundTexture);
    }

    public void clear() {
        for (ShipImage image : this.images.values()) image.texture.deleteGlTexture();
        this.images.clear();
        this.world = null;
    }

    /**
     * Renders interpolated ships using the mod's drawing functions.
     * */
    public void render(
            double cameraX, double cameraZ, float partialTicks, double radius,
            @NotNull MinimapMod mapMod, @Nullable MinimapMod.JourneyMapContext context
    ) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.world == null || this.world != mc.world || !VSConfig.showShipInMinimapMods) return;

        //get renderable ships
        List<PhysicsObject> ships = new ArrayList<>();
        for (PhysicsObject ship : ValkyrienUtils.getPhysosLoadedInWorld(mc.world)) {
            if (!this.images.containsKey(ship.getShipData().getUuid())) continue;
            AxisAlignedBB bounds = ship.getShipBB();
            if (bounds.maxX < cameraX - radius || bounds.minX > cameraX + radius
                    || bounds.maxZ < cameraZ - radius || bounds.minZ > cameraZ + radius
            ) {
                continue;
            }
            ships.add(ship);
        }
        ships.sort(Comparator.comparingDouble(ship -> ship.getShipTransform().getPosY()));
        if (ships.isEmpty()) return;

        //draw the ships
        BiConsumer<DynamicTexture, Vector3d[]> drawImage = mapMod.getDrawing().apply(context);
        int boundTexture = mapMod.getSetup().getAsInt();
        for (PhysicsObject ship : ships) {
            ship.getShipTransformationManager().updateRenderTransform(partialTicks);
            ShipTransform transform = ship.getShipTransformationManager().getRenderTransform();
            ShipImage image = this.images.get(ship.getShipData().getUuid());

            ShipMapRaster.Image raster = image.raster;
            Vector3d[] vertices = ShipMapRaster.projectQuad(raster, image.transform, transform, cameraX, cameraZ);
            drawImage.accept(image.texture, vertices);
        }
        mapMod.getCleanup().accept(boundTexture);
    }

    /**
     * render info onto the map for each ship
     * */
    private static class ShipImage {
        private DynamicTexture texture;
        private ShipMapRaster.Image raster;
        private ShipTransform transform;
        private List<ShipMapRaster.MapBlock> blocks = List.of();
        private long updatedAt;
    }
}