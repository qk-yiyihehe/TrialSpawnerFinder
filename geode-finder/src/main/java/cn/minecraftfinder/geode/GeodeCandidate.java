package cn.minecraftfinder.geode;

public record GeodeCandidate(
        int centerChunkX,
        int centerChunkZ,
        int geodeCount,
        int buddingCount) implements Comparable<GeodeCandidate> {

    public double playerX() {
        return centerChunkX * 16.0 + 8.0;
    }

    public double playerZ() {
        return centerChunkZ * 16.0 + 8.0;
    }

    @Override
    public int compareTo(GeodeCandidate other) {
        int result = Integer.compare(other.buddingCount, buddingCount);
        if (result == 0) result = Integer.compare(other.geodeCount, geodeCount);
        if (result == 0) result = Integer.compare(centerChunkX, other.centerChunkX);
        if (result == 0) result = Integer.compare(centerChunkZ, other.centerChunkZ);
        return result;
    }
}
