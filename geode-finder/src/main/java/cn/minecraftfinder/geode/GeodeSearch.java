package cn.minecraftfinder.geode;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class GeodeSearch {
    private GeodeSearch() {
    }

    public static List<GeodeCandidate> search(GeodeFinderConfig config) {
        int activityRadius = config.minecraftVersion().isAtLeast(1, 21, 5)
                ? config.simulationDistanceChunks()
                : Math.ceilDiv(config.legacyRandomTickRadiusBlocks(), 16);
        int originRadius = activityRadius + 1;
        List<GeodeCandidate> coarse = GeodeWindowScanner.scan(config, originRadius);
        ModernGeodeSimulator simulator = new ModernGeodeSimulator(config.seed());
        Map<Long, GeodeSimulation> cache = new HashMap<>();
        List<GeodeCandidate> refined = new ArrayList<>();
        RandomTickFootprint footprint = config.randomTickFootprint();

        for (GeodeCandidate candidate : coarse) {
            List<BuddingAmethyst> budding = new ArrayList<>();
            int activeGeodes = 0;
            for (int chunkX = candidate.centerChunkX() - originRadius;
                    chunkX <= candidate.centerChunkX() + originRadius; chunkX++) {
                for (int chunkZ = candidate.centerChunkZ() - originRadius;
                        chunkZ <= candidate.centerChunkZ() + originRadius; chunkZ++) {
                    if (!GeodeFinderConfig.isChunkInsideWorld(chunkX, chunkZ)) continue;
                    long key = ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
                    GeodeSimulation simulation;
                    if (cache.containsKey(key)) {
                        simulation = cache.get(key);
                    } else {
                        simulation = simulator.simulate(chunkX, chunkZ);
                        cache.put(key, simulation);
                    }
                    if (simulation == null) continue;
                    if (footprint.containsChunk(
                            candidate.playerX(), candidate.playerZ(), chunkX, chunkZ)) {
                        activeGeodes++;
                    }
                    for (BuddingAmethyst block : simulation.buddingAmethyst()) {
                        if (GeodeFinderConfig.isChunkInsideWorld(block.chunkX(), block.chunkZ())) {
                            budding.add(block);
                        }
                    }
                }
            }
            int count = BuddingActivityCounter.count(
                    candidate.playerX(), candidate.playerZ(), budding, footprint);
            if (activeGeodes >= config.minimumGeodes()
                    && count >= config.minimumBuddingAmethyst()) {
                refined.add(new GeodeCandidate(
                        candidate.centerChunkX(), candidate.centerChunkZ(), activeGeodes, count));
            }
        }
        refined.sort(null);
        return List.copyOf(refined);
    }
}
