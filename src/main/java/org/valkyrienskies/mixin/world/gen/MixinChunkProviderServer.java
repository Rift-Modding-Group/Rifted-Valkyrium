package org.valkyrienskies.mixin.world.gen;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.IChunkGenerator;
import org.jetbrains.annotations.NotNull;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.valkyrienskies.mod.common.ships.chunk_claims.ShipChunkAllocator;
import org.valkyrienskies.mod.common.ships.ship_world.IPhysObjectWorld;
import org.valkyrienskies.mod.common.ships.ship_world.WorldServerShipManager;
import org.valkyrienskies.mod.common.util.ValkyrienUtils;

import java.util.Set;

@Mixin(ChunkProviderServer.class)
public class MixinChunkProviderServer {
    @Shadow @Final
    private Set<Long> droppedChunks;
    @Shadow @Final
    public WorldServer world;

    /**
     * Block early terrain generation in shipyard
     * Should help w most terrain gen mods
     */
    @WrapOperation(
        method = "provideChunk",
        at = @At(value = "INVOKE", target = "Lnet/minecraft/world/gen/IChunkGenerator;generateChunk(II)Lnet/minecraft/world/chunk/Chunk;")
    )
    @NotNull
    private Chunk generateShipyardChunk(@NotNull IChunkGenerator generator, int x, int z, @NotNull Operation<Chunk> original) {
        if (!ShipChunkAllocator.isChunkInShipyard(x, z)) return original.call(generator, x, z);

        Chunk chunk = new Chunk(this.world, x, z);
        chunk.setTerrainPopulated(true);
        chunk.generateSkylightMap();
        return chunk;
    }

    /**
     * Used to prevent the world from unloading the chunks of ships being loaded in the background
     */
    @Inject(method = "tick", at = @At("HEAD"))
    private void preTick(CallbackInfoReturnable<Boolean> cir) {
        IPhysObjectWorld physObjectWorld = ValkyrienUtils.getPhysObjWorld(world);
        if (!(physObjectWorld instanceof WorldServerShipManager serverShipManager)) {
            throw new IllegalStateException("Could not get ship manager from world!");
        }
        for (Long chunkPos : serverShipManager.getBackgroundShipChunks()) {
            droppedChunks.remove(chunkPos);
        }
    }
}
