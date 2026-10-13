package org.valkyrienskies.mod.common.ships.chunk_claims;

import net.minecraft.util.math.ChunkPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VSChunkClaimTest {
    @Test
    void containsOnlyExplicitlyClaimedChunks() {
        VSChunkClaim claim = new VSChunkClaim(new ChunkPos(320000, 3200));

        claim.addChunkClaim(320000, 3200);
        claim.addChunkClaim(320001, 3200);

        assertTrue(claim.containsChunk(320000, 3200));
        assertTrue(claim.containsChunk(320001, 3200));
        assertFalse(claim.containsChunk(319999, 3200));
        assertFalse(claim.containsChunk(320002, 3200));
    }

    @Test
    void removedChunksNoLongerBelongToClaim() {
        VSChunkClaim claim = new VSChunkClaim(new ChunkPos(320000, 3200));
        claim.addChunkClaim(320000, 3200);

        assertTrue(claim.removeChunkClaim(320000, 3200));
        assertFalse(claim.containsChunk(320000, 3200));
    }
}