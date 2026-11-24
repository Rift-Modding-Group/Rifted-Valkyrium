package org.valkyrienskies.mod.client.render;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.block.BlockLiquid;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BufferBuilder;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.BlockRenderLayer;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fluids.IFluidBlock;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.lwjgl.opengl.GL11;
import org.valkyrienskies.mod.common.ships.fluids.ShipWaterVolume;
import org.valkyrienskies.mod.common.ships.ship_transform.ShipTransform;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;

import javax.annotation.ParametersAreNonnullByDefault;
import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Supplies inward fluid surfaces along the boundary of the ship's fluid cutout.
 * */
@ParametersAreNonnullByDefault
public class ShipFluidBoundaryRenderer {
    @Nullable
    private ShipWaterVolume.Snapshot snapshot;
    private int[] faces = new int[0];
    private final Matrix4d transform = new Matrix4d();
    private long worldTick = Long.MIN_VALUE;
    private TextureAtlasSprite atlasStamp;
    private final Map<BlockRenderLayer, BufferBuilder.State> layers = new EnumMap<>(BlockRenderLayer.class);

    public static void render(ShipFluidBoundaryRenderer renderer, PhysicsObject ship, BlockRenderLayer layer, BlockPos offset) {
        ShipWaterVolume.Snapshot current = ship.getWaterVolume().getSnapshot();
        if (current.air().isEmpty() && current.mask().equals(current.dry())) return;
        Minecraft minecraft = Minecraft.getMinecraft();
        World world = ship.getWorld();
        ShipTransform renderTransform = ship.getShipTransformationManager().getRenderTransform();
        TextureAtlasSprite stamp = minecraft.getTextureMapBlocks().getAtlasSprite("minecraft:blocks/water_still");
        if (renderer.snapshot != current || renderer.worldTick != world.getTotalWorldTime()
                || !renderer.transform.equals(renderTransform.getSubspaceToGlobal()) || renderer.atlasStamp != stamp) {
            if (renderer.snapshot != current) renderer.faces = collectFaces(current);
            renderer.snapshot = current;
            renderer.worldTick = world.getTotalWorldTime();
            renderer.transform.set(renderTransform.getSubspaceToGlobal());
            renderer.atlasStamp = stamp;
            renderer.layers.clear();
            Map<BlockRenderLayer, BufferBuilder> builders = new EnumMap<>(BlockRenderLayer.class);
            for (Face face : build(world, current, renderTransform, renderer.faces)) {
                Fluid fluid = FluidRegistry.lookupFluidForBlock(face.state().getBlock());
                if (face.state().getBlock() instanceof IFluidBlock block) fluid = block.getFluid();
                if (fluid == null) continue;
                ResourceLocation texture = fluid.getFlowing();
                if (face.side().getAxis() == EnumFacing.Axis.Y || texture == null) texture = fluid.getStill();
                if (texture == null) continue;
                TextureAtlasSprite sprite = minecraft.getTextureMapBlocks().getAtlasSprite(texture.toString());
                int color = fluid.getColor(world, face.worldPos());
                if (face.state().getBlock() instanceof BlockLiquid) {
                    color = 0xFF000000 | minecraft.getBlockColors().colorMultiplier(face.state(), world, face.worldPos(), 0) & 0xFFFFFF;
                }
                int light = world.getCombinedLight(face.worldPos(), fluid.getLuminosity(world, face.worldPos()));
                for (BlockRenderLayer target : BlockRenderLayer.values()) {
                    if (!face.state().getBlock().canRenderInLayer(face.state(), target)) continue;
                    BufferBuilder builder = builders.computeIfAbsent(target, ignored -> {
                        BufferBuilder buffer = new BufferBuilder(4096);
                        buffer.begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
                        return buffer;
                    });
                    for (int triangle = 1; triangle < face.vertices().size() - 1; triangle++) {
                        for (int corner : new int[]{0, triangle, triangle + 1, triangle + 1}) {
                            Vector3d point = face.vertices().get(corner);
                            double u;
                            double v;
                            if (face.side().getAxis() == EnumFacing.Axis.X) u = point.z - face.localPos().getZ();
                            else u = point.x - face.localPos().getX();
                            if (face.side().getAxis() == EnumFacing.Axis.Y) v = point.z - face.localPos().getZ();
                            else v = 1D - (point.y - face.localPos().getY());
                            builder.pos(point.x - offset.getX(), point.y - offset.getY(), point.z - offset.getZ())
                                    .color(color >> 16 & 255, color >> 8 & 255, color & 255, color >>> 24)
                                    .tex(sprite.getInterpolatedU(u * 16D), sprite.getInterpolatedV(v * 16D))
                                    .lightmap(light & 65535, light >> 16 & 65535).endVertex();
                        }
                    }
                }
            }
            for (Map.Entry<BlockRenderLayer, BufferBuilder> entry : builders.entrySet()) {
                renderer.layers.put(entry.getKey(), entry.getValue().getVertexState());
                entry.getValue().finishDrawing();
            }
        }
        BufferBuilder.State state = renderer.layers.get(layer);
        if (state == null) return;
        minecraft.getTextureManager().bindTexture(TextureMap.LOCATION_BLOCKS_TEXTURE);
        Tessellator tessellator = Tessellator.getInstance();
        tessellator.getBuffer().begin(GL11.GL_QUADS, DefaultVertexFormats.BLOCK);
        tessellator.getBuffer().setVertexState(state);
        tessellator.draw();
    }

    public static int[] collectFaces(ShipWaterVolume.Snapshot snapshot) {
        IntArrayList faces = new IntArrayList();
        for (int index = snapshot.mask().nextSetBit(0); index >= 0; index = snapshot.mask().nextSetBit(index + 1)) {
            int x = index % snapshot.sizeX();
            int y = index / snapshot.sizeX() % snapshot.sizeY();
            int z = index / (snapshot.sizeX() * snapshot.sizeY());
            for (EnumFacing side : EnumFacing.values()) {
                int nx = x + side.getXOffset();
                int ny = y + side.getYOffset();
                int nz = z + side.getZOffset();
                if (nx >= 0 && nx < snapshot.sizeX() && ny >= 0 && ny < snapshot.sizeY() && nz >= 0 && nz < snapshot.sizeZ()
                        && snapshot.mask().get(nx + snapshot.sizeX() * (ny + snapshot.sizeY() * nz))) continue;
                faces.add(index * 6 + side.getIndex());
            }
        }
        return faces.toIntArray();
    }

    public static List<Face> build(World world, ShipWaterVolume.Snapshot snapshot, ShipTransform transform, int[] faces) {
        List<Face> surfaces = new ArrayList<>();
        Long2ObjectOpenHashMap<ShipWaterVolume.FluidSample> samples = new Long2ObjectOpenHashMap<>();
        for (int encoded : faces) {
            int index = encoded / 6;
            EnumFacing side = EnumFacing.byIndex(encoded % 6);
            BlockPos pos = snapshot.origin().add(index % snapshot.sizeX(), index / snapshot.sizeX() % snapshot.sizeY(),
                    index / (snapshot.sizeX() * snapshot.sizeY()));
            int axis = switch (side.getAxis()) { case X -> 0; case Y -> 1; case Z -> 2; };
            List<Vector3d> polygon = new ArrayList<>(4);
            Vector3d minimum = new Vector3d(Double.MAX_VALUE);
            Vector3d maximum = new Vector3d(-Double.MAX_VALUE);
            for (int corner = 0; corner < 4; corner++) {
                int ordered = side.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 3 - corner : corner;
                Vector3d point = new Vector3d(pos.getX(), pos.getY(), pos.getZ());
                point.setComponent(axis, point.get(axis) + (side.getAxisDirection() == EnumFacing.AxisDirection.POSITIVE ? 1D : 0D));
                if (ordered == 1 || ordered == 2) point.setComponent((axis + 1) % 3, point.get((axis + 1) % 3) + 1D);
                if (ordered >= 2) point.setComponent((axis + 2) % 3, point.get((axis + 2) % 3) + 1D);
                point.add(side.getXOffset() * 0.0001D, side.getYOffset() * 0.0001D, side.getZOffset() * 0.0001D);
                transform.getSubspaceToGlobal().transformPosition(point);
                polygon.add(point);
                minimum.min(point);
                maximum.max(point);
            }
            for (int z = (int) Math.floor(minimum.z); z <= Math.floor(maximum.z); z++) {
                for (int y = (int) Math.floor(minimum.y); y <= Math.floor(maximum.y); y++) {
                    for (int x = (int) Math.floor(minimum.x); x <= Math.floor(maximum.x); x++) {
                        BlockPos worldPos = new BlockPos(x, y, z);
                        long key = worldPos.toLong();
                        if (!samples.containsKey(key)) samples.put(key, ShipWaterVolume.fluidSample(world, worldPos));
                        ShipWaterVolume.FluidSample fluid = samples.get(key);
                        if (fluid == null) continue;
                        List<Vector3d> clipped = polygon;
                        for (int component = 0; component < 3 && clipped.size() >= 3; component++) {
                            double lower = switch (component) { case 0 -> x; case 1 -> fluid.minY(); default -> z; };
                            double upper = switch (component) { case 0 -> x + 1D; case 1 -> fluid.maxY(); default -> z + 1D; };
                            clipped = clip(clipped, component, lower, true);
                            if (clipped.size() >= 3) clipped = clip(clipped, component, upper, false);
                        }
                        if (clipped.size() < 3) continue;
                        double area = 0D;
                        for (int i = 1; i < clipped.size() - 1; i++) {
                            area += new Vector3d(clipped.get(i)).sub(clipped.getFirst())
                                    .cross(new Vector3d(clipped.get(i + 1)).sub(clipped.getFirst())).length();
                        }
                        if (area < 0.00000001D) continue;
                        List<Vector3d> local = new ArrayList<>(clipped.size());
                        for (Vector3d point : clipped) local.add(transform.getGlobalToSubspace().transformPosition(point, new Vector3d()));
                        surfaces.add(new Face(fluid.state(), worldPos, pos, side, local));
                    }
                }
            }
        }
        return surfaces;
    }

    private static List<Vector3d> clip(List<Vector3d> polygon, int axis, double boundary, boolean lower) {
        List<Vector3d> clipped = new ArrayList<>();
        Vector3d previous = polygon.getLast();
        double previousDistance = previous.get(axis) - boundary;
        if (!lower) previousDistance = -previousDistance;
        for (Vector3d point : polygon) {
            double distance = point.get(axis) - boundary;
            if (!lower) distance = -distance;
            if ((distance >= 0D) != (previousDistance >= 0D)) {
                clipped.add(new Vector3d(previous).lerp(point, previousDistance / (previousDistance - distance)));
            }
            if (distance >= 0D) clipped.add(point);
            previous = point;
            previousDistance = distance;
        }
        return clipped;
    }

    public record Face(IBlockState state, BlockPos worldPos, BlockPos localPos, EnumFacing side, List<Vector3d> vertices) {}
}
