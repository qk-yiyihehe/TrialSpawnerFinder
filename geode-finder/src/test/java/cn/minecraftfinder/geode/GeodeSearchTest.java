package cn.minecraftfinder.geode;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.SearchArea;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeodeSearchTest {
    @Test
    void reportsCoarseAndRefinementProgress() {
        List<String> phases = new ArrayList<>();

        GeodeSearch.search(config(0, AreaShape.SQUARE, 50),
                update -> phases.add(update.phase()));

        assertTrue(phases.contains("粗筛"));
        assertTrue(phases.contains("理论精排"));
    }

    @Test
    void runsTwoStageSearchAndReturnsRankedCandidates() {
        GeodeFinderConfig config = config(0, AreaShape.SQUARE, 500);

        List<GeodeCandidate> results = GeodeSearch.search(config);

        assertFalse(results.isEmpty());
        for (int index = 1; index < results.size(); index++) {
            assertTrue(results.get(index - 1).buddingCount() >= results.get(index).buddingCount());
        }
    }

    @Test
    void reportsGeodesInTheActualActivityFootprint() {
        List<GeodeCandidate> results = GeodeSearch.search(config(0, AreaShape.SQUARE, 500));

        assertTrue(results.stream().allMatch(candidate -> candidate.geodeCount() <= 25));
    }

    @Test
    void oversizedPrefilterFindsTheSameBestBuddingCountForSmallReferenceAreas() {
        for (long seed : List.of(0L, 12345L)) {
            for (AreaShape shape : AreaShape.values()) {
                List<GeodeCandidate> exhaustive = GeodeSearch.search(config(seed, shape, 500));
                List<GeodeCandidate> reduced = GeodeSearch.search(config(seed, shape, 100));

                assertFalse(exhaustive.isEmpty());
                assertFalse(reduced.isEmpty());
                assertEquals(exhaustive.getFirst().buddingCount(), reduced.getFirst().buddingCount(),
                        "seed=" + seed + ", shape=" + shape);
            }
        }
    }

    private static GeodeFinderConfig config(long seed, AreaShape shape, int prefilterLimit) {
        return new GeodeFinderConfig(
                seed,
                MinecraftVersion.parse("1.21.5"),
                new SearchArea(0, 0, 96, shape, false, 30_000_000),
                128,
                2,
                1,
                1,
                prefilterLimit,
                0,
                20,
                4,
                64);
    }
}
