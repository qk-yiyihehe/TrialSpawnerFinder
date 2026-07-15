package cn.minecraftfinder.geode;

public record LegacyRandomTickFootprint(int radiusBlocks) implements RandomTickFootprint {
    public LegacyRandomTickFootprint {
        if (radiusBlocks <= 0) {
            throw new IllegalArgumentException("旧版随机刻半径必须大于 0");
        }
    }

    @Override
    public boolean containsChunk(double playerX, double playerZ, int chunkX, int chunkZ) {
        double dx = chunkX * 16.0 + 8.0 - playerX;
        double dz = chunkZ * 16.0 + 8.0 - playerZ;
        return dx * dx + dz * dz < (double) radiusBlocks * radiusBlocks;
    }
}
