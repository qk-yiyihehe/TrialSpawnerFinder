package cn.minecraftfinder.geode;

public record BuddingAmethyst(int x, int y, int z) {
    public int chunkX() {
        return Math.floorDiv(x, 16);
    }

    public int chunkZ() {
        return Math.floorDiv(z, 16);
    }
}
