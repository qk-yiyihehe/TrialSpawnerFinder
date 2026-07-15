package cn.minecraftfinder.geode;

public sealed interface RandomTickFootprint
        permits LegacyRandomTickFootprint, SimulationDistanceFootprint {

    boolean containsChunk(double playerX, double playerZ, int chunkX, int chunkZ);

    static RandomTickFootprint forVersion(
            MinecraftVersion version, int legacyRadiusBlocks, int simulationDistanceChunks) {
        return version.isAtLeast(1, 21, 5)
                ? new SimulationDistanceFootprint(simulationDistanceChunks)
                : new LegacyRandomTickFootprint(legacyRadiusBlocks);
    }
}
