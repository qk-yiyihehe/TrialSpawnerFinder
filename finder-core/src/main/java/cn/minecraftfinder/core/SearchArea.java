package cn.minecraftfinder.core;

public record SearchArea(
        int centerX,
        int centerZ,
        int radiusBlocks,
        AreaShape shape,
        boolean fullWorld,
        int worldLimit) {

    public SearchArea {
        if (!fullWorld && radiusBlocks <= 0) {
            throw new IllegalArgumentException("普通搜索的搜索半径必须大于 0");
        }
        if (worldLimit <= 0) {
            throw new IllegalArgumentException("世界边界必须大于 0");
        }
        if (!fullWorld
                && (Math.abs((long) centerX) > worldLimit || Math.abs((long) centerZ) > worldLimit)) {
            throw new IllegalArgumentException("搜索中心超出了世界边界");
        }
    }

    public SearchBounds bounds() {
        return fullWorld
                ? SearchBounds.fullWorld(worldLimit)
                : SearchBounds.around(centerX, centerZ, radiusBlocks, worldLimit);
    }

    public boolean contains(long x, long z) {
        return bounds().contains(x, z)
                && (fullWorld || shape.contains(centerX, centerZ, x, z, radiusBlocks));
    }
}
