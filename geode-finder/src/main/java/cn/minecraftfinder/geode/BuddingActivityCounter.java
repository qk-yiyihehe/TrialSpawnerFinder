package cn.minecraftfinder.geode;

import java.util.List;

public final class BuddingActivityCounter {
    private BuddingActivityCounter() {
    }

    public static int count(
            double playerX,
            double playerZ,
            List<BuddingAmethyst> budding,
            RandomTickFootprint footprint) {
        int count = 0;
        for (BuddingAmethyst block : budding) {
            if (footprint.containsChunk(playerX, playerZ, block.chunkX(), block.chunkZ())) {
                count++;
            }
        }
        return count;
    }
}
