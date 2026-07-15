package cn.minecraftfinder.geode.dev;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.SearchArea;
import cn.minecraftfinder.geode.GeodeCandidate;
import cn.minecraftfinder.geode.GeodeFinderConfig;
import cn.minecraftfinder.geode.MinecraftVersion;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BatchMinecraftGeodeVerifierTest {
    @Test
    void requiredChunksAreDeduplicatedAndClippedAtTheNegativeWorldEdge() {
        GeodeFinderConfig config = new GeodeFinderConfig(
                0, MinecraftVersion.parse("1.21.1"),
                new SearchArea(-29_999_992, 0, 128, AreaShape.SQUARE, false, 30_000_000),
                128, 10, 1, 0, 10, 2, 2, 1, 256);
        GeodeCandidate candidate = new GeodeCandidate(-1_875_000, 0, 1, 1);
        BatchMinecraftGeodeVerifier verifier = new BatchMinecraftGeodeVerifier(null, config);

        Set<Long> once = verifier.requiredChunkKeys(List.of(candidate));
        Set<Long> twice = verifier.requiredChunkKeys(List.of(candidate, candidate));

        assertEquals(once, twice);
        assertTrue(once.contains(key(-1_875_000, 0)));
        assertFalse(once.contains(key(-1_875_001, 0)));
        assertTrue(once.stream().allMatch(chunkKey -> GeodeFinderConfig.isChunkInsideWorld(
                (int) (chunkKey >> 32), (int) (long) chunkKey)));
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }
}
