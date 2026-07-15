package cn.minecraftfinder.geode;

public record SimulationDistanceFootprint(int distanceChunks) implements RandomTickFootprint {
    public SimulationDistanceFootprint {
        if (distanceChunks < 2 || distanceChunks > 32) {
            throw new IllegalArgumentException("simulation distance 必须为 2..32");
        }
    }

    @Override
    public boolean containsChunk(double playerX, double playerZ, int chunkX, int chunkZ) {
        int playerChunkX = Math.floorDiv((int) Math.floor(playerX), 16);
        int playerChunkZ = Math.floorDiv((int) Math.floor(playerZ), 16);
        return Math.abs(chunkX - playerChunkX) <= distanceChunks
                && Math.abs(chunkZ - playerChunkZ) <= distanceChunks;
    }
}
