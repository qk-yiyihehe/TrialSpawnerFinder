package cn.minecraftfinder.geode;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.SearchArea;
import cn.minecraftfinder.core.SearchBounds;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeodeWindowScannerTest {
    @Test
    void rollingWindowMatchesNaiveTopCandidates() {
        GeodeFinderConfig config = config(
                0, new SearchArea(0, 0, 256, AreaShape.SQUARE, false, 30_000_000),
                40, 64);
        int radius = 3;

        List<GeodeCandidate> candidates = GeodeWindowScanner.scan(config, radius);

        assertEquals(naiveTop(config, radius), candidates);
    }

    @Test
    void shardedScanKeepsTheSameGlobalTopCandidates() {
        SearchArea area = new SearchArea(0, 0, 256, AreaShape.SQUARE, false, 30_000_000);
        GeodeFinderConfig oneShard = config(12345, area, 40, 10_000);
        GeodeFinderConfig manyShards = config(12345, area, 40, 64);

        assertEquals(
                GeodeWindowScanner.scan(oneShard, 9),
                GeodeWindowScanner.scan(manyShards, 9));
    }

    @Test
    void circleAndSquareNeverReturnCentersOutsideTheSearchArea() {
        for (AreaShape shape : AreaShape.values()) {
            SearchArea area = new SearchArea(100, -100, 160, shape, false, 30_000_000);
            GeodeFinderConfig config = config(0, area, 1_000, 64);

            List<GeodeCandidate> candidates = GeodeWindowScanner.scan(config, 3);

            assertTrue(candidates.stream().allMatch(candidate -> area.contains(
                    Math.round(candidate.playerX()), Math.round(candidate.playerZ()))));
        }
    }

    @Test
    void keepsOnlyConfiguredBestCoarseCandidates() {
        GeodeFinderConfig config = config(
                0, new SearchArea(0, 0, 512, AreaShape.SQUARE, false, 30_000_000),
                25, 256);

        List<GeodeCandidate> candidates = GeodeWindowScanner.scan(config, 9);

        assertEquals(25, candidates.size());
        assertTrue(candidates.getFirst().geodeCount() >= candidates.getLast().geodeCount());
    }

    private static GeodeFinderConfig config(
            long seed, SearchArea area, int prefilterLimit, int shardSizeBlocks) {
        return new GeodeFinderConfig(
                seed, MinecraftVersion.parse("1.21.1"), area,
                128, 10, 1, 0, prefilterLimit, 0, 20, 4, shardSizeBlocks);
    }

    private static List<GeodeCandidate> naiveTop(GeodeFinderConfig config, int radius) {
        SearchBounds bounds = config.searchArea().bounds();
        int centerMinX = Math.floorDiv(Math.toIntExact(bounds.minX()), 16);
        int centerMaxX = Math.floorDiv(Math.toIntExact(bounds.maxX()), 16);
        int centerMinZ = Math.floorDiv(Math.toIntExact(bounds.minZ()), 16);
        int centerMaxZ = Math.floorDiv(Math.toIntExact(bounds.maxZ()), 16);
        ModernGeodeSimulator simulator = new ModernGeodeSimulator(config.seed());
        List<GeodeCandidate> candidates = new ArrayList<>();
        for (int centerX = centerMinX; centerX <= centerMaxX; centerX++) {
            for (int centerZ = centerMinZ; centerZ <= centerMaxZ; centerZ++) {
                long blockX = centerX * 16L + 8L;
                long blockZ = centerZ * 16L + 8L;
                if (!config.searchArea().contains(blockX, blockZ)) continue;
                int count = 0;
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                        if (GeodeFinderConfig.isChunkInsideWorld(x, z)
                                && simulator.isGeodeChunk(x, z)) {
                            count++;
                        }
                    }
                }
                if (count >= config.minimumGeodes()) {
                    candidates.add(new GeodeCandidate(centerX, centerZ, count, 0));
                }
            }
        }
        candidates.sort(null);
        return List.copyOf(candidates.subList(
                0, Math.min(config.prefilterLimit(), candidates.size())));
    }
}
