package cn.minecraftfinder.geode;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BuddingActivityCounterTest {
    @Test
    void countsWholeSelectedChunksInsteadOfBlockDistance() {
        List<BuddingAmethyst> blocks = List.of(
                new BuddingAmethyst(127, 0, 15),
                new BuddingAmethyst(128, 0, 0));

        int count = BuddingActivityCounter.count(
                0, 0, blocks, new LegacyRandomTickFootprint(128));

        assertEquals(1, count);
    }
}
