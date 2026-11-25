package org.valkyrienskies.mod.client.render;

import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.culling.ICamera;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;
import net.minecraftforge.fml.common.Loader;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.entity.Entity;
import org.joml.Matrix4d;
import org.joml.Vector3d;
import org.joml.Vector4d;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;
import org.lwjgl.opengl.GLContext;
import org.valkyrienskies.mod.common.ValkyrienSkiesMod;
import org.valkyrienskies.mod.client.compat.FairyLightsLighting;
import org.valkyrienskies.mod.common.ships.fluids.ShipWaterVolume;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.PhysicsObject;
import org.valkyrienskies.mod.common.ships.ship_world.WorldClientShipManager;
import net.minecraft.world.chunk.Chunk;

import javax.annotation.ParametersAreNonnullByDefault;
import java.io.IOException;
import java.io.InputStream;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Comparator;

/**
 * Clips fluid fragments against ship-local voxels while retaining other translucent terrain.
 * */
@ParametersAreNonnullByDefault
public class ShipWaterRenderer {
    private int program;
    private int texture;
    private int spriteTexture;
    private int previousSpriteTexture;
    private TextureAtlasSprite atlasStamp;
    private int textureWidth;
    private int textureHeight;
    private int metadataHeight;
    private int maxTextureSize;
    private int previousTexture;
    private int previousActiveTexture;
    private boolean active;
    private boolean failed;
    private boolean warnedShader;
    private List<ShipWaterVolume.Snapshot> uploaded = List.of();
    private Map<String, Integer> uniforms = new HashMap<>();
    private FloatBuffer modelView = BufferUtils.createFloatBuffer(16);
    private FloatBuffer metadata = BufferUtils.createFloatBuffer(16);
    private FloatBuffer lightData = BufferUtils.createFloatBuffer(ShipDynamicLighting.MAX_SHADER_LIGHTS * 4);

    public static void begin(ShipWaterRenderer renderer, IPhysObjectWorld manager, ICamera camera, Entity view, double partialTicks) {
        if (renderer.failed) return;
        double viewX = view.lastTickPosX + (view.posX - view.lastTickPosX) * partialTicks;
        double viewY = view.lastTickPosY + (view.posY - view.lastTickPosY) * partialTicks;
        double viewZ = view.lastTickPosZ + (view.posZ - view.lastTickPosZ) * partialTicks;
        List<ShipDynamicLighting.Light> lights = new ArrayList<>();
        Set<Chunk> retainedChunks = new HashSet<>();
        List<PhysicsObject> ships = new ArrayList<>();
        List<ShipWaterVolume.Snapshot> snapshots = new ArrayList<>();
        for (PhysicsObject ship : manager.getAllLoadedPhysObj()) {
            if (manager instanceof WorldClientShipManager client) {
                lights.addAll(ShipDynamicLighting.collect(
                        client.dynamicLighting, manager.getWorld(),
                        ship.getClaimedChunkCache(), ship.getShipTransformationManager().getRenderTransform()
                ));
                if (Loader.isModLoaded("fairylights")) {
                    FairyLightsLighting.collectLights(ship.getClaimedChunkCache(),
                            ship.getShipTransformationManager().getCurrentTickTransform(),
                            ship.getShipTransformationManager().getRenderTransform(), (float) partialTicks, lights
                    );
                }
                for (Chunk chunk : ship.getClaimedChunkCache()) retainedChunks.add(chunk);
            }
            ShipWaterVolume.Snapshot snapshot = ship.getWaterVolume().getSnapshot();
            if ((snapshot.air().isEmpty() && snapshot.mask().equals(snapshot.dry()))
                    || !ship.getShipRenderer().shouldRender(camera)) continue;
            ships.add(ship);
            snapshots.add(snapshot);
        }

        //closest 32 sources bound fragment work, use tiled light lists for larger fleets
        lights.sort(Comparator.comparingDouble(light -> {
            double dx = light.x() - viewX;
            double dy = light.y() - viewY;
            double dz = light.z() - viewZ;
            return dx * dx + dy * dy + dz * dz;
        }));
        if (lights.size() > ShipDynamicLighting.MAX_SHADER_LIGHTS) {
            lights = new ArrayList<>(lights.subList(0, ShipDynamicLighting.MAX_SHADER_LIGHTS));
        }
        if (manager instanceof WorldClientShipManager client) {
            ShipDynamicLighting.update(client.dynamicLighting, manager.getWorld(), lights);
            ShipDynamicLighting.retainChunks(client.dynamicLighting, retainedChunks);
        }
        if (ships.isEmpty() && lights.isEmpty()) {
            if (renderer.texture != 0) GL11.glDeleteTextures(renderer.texture);
            renderer.texture = 0;
            renderer.uploaded = List.of();
            return;
        }
        if (GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM) != 0) {
            if (!renderer.warnedShader) {
                ValkyrienSkiesMod.LOGGER.warn("Ship fluid cutouts require the fixed-function terrain renderer; an external shader is active.");
                renderer.warnedShader = true;
            }
            return;
        }
        if (renderer.program == 0) {
            renderer.program = GL20.glCreateProgram();
            for (int shaderType : new int[]{GL20.GL_VERTEX_SHADER, GL20.GL_FRAGMENT_SHADER}) {
                String extension = shaderType == GL20.GL_VERTEX_SHADER ? "vert" : "frag";
                int shader = GL20.glCreateShader(shaderType);
                try (InputStream stream = ShipWaterRenderer.class.getResourceAsStream("/assets/valkyrienskies/shaders/ship_water." + extension)) {
                    if (stream == null) throw new IOException("Missing ship fluid shader");
                    GL20.glShaderSource(shader, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                    GL20.glCompileShader(shader);
                    if (GL20.glGetShaderi(shader, GL20.GL_COMPILE_STATUS) == 0) throw new IOException(GL20.glGetShaderInfoLog(shader, 8192));
                    GL20.glAttachShader(renderer.program, shader);
                    GL20.glDeleteShader(shader);
                }
                catch (IOException exception) {
                    GL20.glDeleteShader(shader);
                    GL20.glDeleteProgram(renderer.program);
                    renderer.program = 0;
                    renderer.failed = true;
                    ValkyrienSkiesMod.LOGGER.error("Could not create ship fluid shader", exception);
                    return;
                }
            }
            GL20.glLinkProgram(renderer.program);
            if (GL20.glGetProgrami(renderer.program, GL20.GL_LINK_STATUS) == 0) {
                ValkyrienSkiesMod.LOGGER.error("Could not link ship fluid shader: {}", GL20.glGetProgramInfoLog(renderer.program, 8192));
                GL20.glDeleteProgram(renderer.program);
                renderer.program = 0;
                renderer.failed = true;
                return;
            }
            for (String name : new String[]{"atlas", "lightmap", "volumes", "volumeTextureSize", "shipCount",
                    "firstShipData[0]", "fogMode", "fogEnabled", "fogRadial", "alphaThreshold",
                    "fluidSprites", "clipFluids", "shipLightCount", "shipLights[0]"}) {
                renderer.uniforms.put(name, GL20.glGetUniformLocation(renderer.program, name));
            }
            renderer.maxTextureSize = GL11.glGetInteger(GL11.GL_MAX_TEXTURE_SIZE);
            renderer.textureWidth = Math.min(1024, renderer.maxTextureSize);
        }

        renderer.previousActiveTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        TextureMap atlas = Minecraft.getMinecraft().getTextureMapBlocks();
        TextureAtlasSprite atlasStamp = atlas.getAtlasSprite("minecraft:blocks/water_still");
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        renderer.previousSpriteTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        if (renderer.spriteTexture == 0 || renderer.atlasStamp != atlasStamp) {
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            int previousAtlas = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, atlas.getGlTextureId());
            int width = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_WIDTH);
            int height = GL11.glGetTexLevelParameteri(GL11.GL_TEXTURE_2D, 0, GL11.GL_TEXTURE_HEIGHT);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previousAtlas);
            ByteBuffer sprites = BufferUtils.createByteBuffer(width * height);
            Set<ResourceLocation> textures = new HashSet<>();
            for (Fluid fluid : FluidRegistry.getRegisteredFluids().values()) {
                if (fluid.getStill() != null) textures.add(fluid.getStill());
                if (fluid.getFlowing() != null) textures.add(fluid.getFlowing());
                if (fluid.getOverlay() != null) textures.add(fluid.getOverlay());
            }
            for (ResourceLocation name : textures) {
                TextureAtlasSprite sprite = atlas.getAtlasSprite(name.toString());
                if (sprite == atlas.getMissingSprite()) continue;
                int minX = Math.clamp((int) Math.floor(sprite.getMinU() * width), 0, width);
                int maxX = Math.clamp((int) Math.ceil(sprite.getMaxU() * width), 0, width);
                int minY = Math.clamp((int) Math.floor(sprite.getMinV() * height), 0, height);
                int maxY = Math.clamp((int) Math.ceil(sprite.getMaxV() * height), 0, height);
                for (int y = minY; y < maxY; y++) {
                    for (int x = minX; x < maxX; x++) sprites.put(x + width * y, (byte) 255);
                }
            }
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            if (renderer.spriteTexture == 0) renderer.spriteTexture = GL11.glGenTextures();
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderer.spriteTexture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_R8, width, height, 0, GL11.GL_RED, GL11.GL_UNSIGNED_BYTE, sprites);
            renderer.atlasStamp = atlasStamp;
        }
        else GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderer.spriteTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        renderer.previousTexture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        boolean newTexture = renderer.texture == 0;
        if (newTexture) renderer.texture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderer.texture);
        renderer.metadataHeight = (ships.size() * 4 + renderer.textureWidth - 1) / renderer.textureWidth;
        int voxelOffset = renderer.metadataHeight * renderer.textureWidth;
        int pixels = voxelOffset;
        for (ShipWaterVolume.Snapshot snapshot : snapshots) pixels += (snapshot.sizeX() * snapshot.sizeY() * snapshot.sizeZ() + 3) / 4;
        renderer.textureHeight = Math.max(1, (pixels + renderer.textureWidth - 1) / renderer.textureWidth);
        if (renderer.textureHeight > renderer.maxTextureSize) {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderer.previousTexture);
            GL13.glActiveTexture(GL13.GL_TEXTURE3);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderer.previousSpriteTexture);
            GL13.glActiveTexture(renderer.previousActiveTexture);
            ValkyrienSkiesMod.LOGGER.warn("Ship fluid cutout volumes exceed the GPU texture size.");
            renderer.failed = true;
            return;
        }
        boolean changed = newTexture || snapshots.size() != renderer.uploaded.size();
        for (int i = 0; !changed && i < snapshots.size(); i++) changed = snapshots.get(i) != renderer.uploaded.get(i);
        if (changed) {
            FloatBuffer cells = BufferUtils.createFloatBuffer(renderer.textureWidth * renderer.textureHeight * 4);
            int offset = voxelOffset * 4;
            for (ShipWaterVolume.Snapshot snapshot : snapshots) {
                for (int index = snapshot.mask().nextSetBit(0); index >= 0; index = snapshot.mask().nextSetBit(index + 1)) {
                    cells.put(offset + index, 1F);
                }
                offset += ((snapshot.sizeX() * snapshot.sizeY() * snapshot.sizeZ() + 3) / 4) * 4;
            }
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL30.GL_RGBA32F, renderer.textureWidth, renderer.textureHeight,
                    0, GL11.GL_RGBA, GL11.GL_FLOAT, cells);
            renderer.uploaded = snapshots;
        }
        renderer.modelView.clear();
        GL11.glGetFloat(GL11.GL_MODELVIEW_MATRIX, renderer.modelView);
        Matrix4d cameraToEye = new Matrix4d().set(renderer.modelView);
        Matrix4d eyeToCamera = cameraToEye.invert(new Matrix4d());
        int metadataFloats = ships.size() <= 1 ? 16 : renderer.metadataHeight * renderer.textureWidth * 4;
        if (renderer.metadata.capacity() < metadataFloats) renderer.metadata = BufferUtils.createFloatBuffer(metadataFloats);
        FloatBuffer metadata = renderer.metadata;
        metadata.clear();
        metadata.limit(metadataFloats);
        for (int i = 0; i < ships.size(); i++) {
            ShipWaterVolume.Snapshot snapshot = snapshots.get(i);
            Matrix4d eyeToGrid = new Matrix4d().translation(-snapshot.origin().getX(), -snapshot.origin().getY(), -snapshot.origin().getZ())
                    .mul(ships.get(i).getShipTransformationManager().getRenderTransform().getGlobalToSubspace())
                    .translate(viewX, viewY, viewZ).mul(eyeToCamera);
            for (int row = 0; row < 3; row++) {
                Vector4d values = eyeToGrid.getRow(row, new Vector4d());
                metadata.put((float) values.x).put((float) values.y).put((float) values.z).put((float) values.w);
            }
            metadata.put(snapshot.sizeX()).put(snapshot.sizeY()).put(snapshot.sizeZ()).put(voxelOffset);
            voxelOffset += (snapshot.sizeX() * snapshot.sizeY() * snapshot.sizeZ() + 3) / 4;
        }
        metadata.position(0);
        // A single hull needs no texture update when only the camera or ship transform moves.
        if (ships.size() > 1) {
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, renderer.textureWidth, renderer.metadataHeight, GL11.GL_RGBA, GL11.GL_FLOAT, metadata);
        }
        GL13.glActiveTexture(renderer.previousActiveTexture);
        GL20.glUseProgram(renderer.program);
        metadata.limit(16);
        GL20.glUniform4(renderer.uniforms.get("firstShipData[0]"), metadata);
        GL20.glUniform1i(renderer.uniforms.get("atlas"), 0);
        GL20.glUniform1i(renderer.uniforms.get("lightmap"), 1);
        GL20.glUniform1i(renderer.uniforms.get("volumes"), 2);
        GL20.glUniform2f(renderer.uniforms.get("volumeTextureSize"), renderer.textureWidth, renderer.textureHeight);
        GL20.glUniform1i(renderer.uniforms.get("shipCount"), ships.size());
        GL20.glUniform1i(renderer.uniforms.get("fogMode"), GL11.glGetInteger(GL11.GL_FOG_MODE));
        GL20.glUniform1i(renderer.uniforms.get("fogRadial"), GLContext.getCapabilities().GL_NV_fog_distance ? 1 : 0);
        GL20.glUniform1i(renderer.uniforms.get("fogEnabled"), GL11.glIsEnabled(GL11.GL_FOG) ? 1 : 0);
        float alphaThreshold = GL11.glIsEnabled(GL11.GL_ALPHA_TEST) ? GL11.glGetFloat(GL11.GL_ALPHA_TEST_REF) : -1F;
        GL20.glUniform1f(renderer.uniforms.get("alphaThreshold"), alphaThreshold);
        GL20.glUniform1i(renderer.uniforms.get("fluidSprites"), 3);
        GL20.glUniform1i(renderer.uniforms.get("clipFluids"), 0);
        GL20.glUniform1i(renderer.uniforms.get("shipLightCount"), lights.size());
        renderer.lightData.clear();
        for (ShipDynamicLighting.Light light : lights) {
            Vector3d eye = cameraToEye.transformPosition(new Vector3d(light.x() - viewX, light.y() - viewY, light.z() - viewZ));
            renderer.lightData.put((float) eye.x).put((float) eye.y).put((float) eye.z).put(light.level());
        }
        renderer.lightData.flip();
        if (!lights.isEmpty()) GL20.glUniform4(renderer.uniforms.get("shipLights[0]"), renderer.lightData);
        renderer.active = true;
    }

    public static void clipWorldFluids(ShipWaterRenderer renderer) {
        if (renderer.active) GL20.glUniform1i(renderer.uniforms.get("clipFluids"), 1);
    }

    public static void end(ShipWaterRenderer renderer) {
        if (!renderer.active) return;
        GL20.glUseProgram(0);
        GL13.glActiveTexture(GL13.GL_TEXTURE2);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderer.previousTexture);
        GL13.glActiveTexture(GL13.GL_TEXTURE3);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, renderer.previousSpriteTexture);
        GL13.glActiveTexture(renderer.previousActiveTexture);
        renderer.active = false;
    }
}
