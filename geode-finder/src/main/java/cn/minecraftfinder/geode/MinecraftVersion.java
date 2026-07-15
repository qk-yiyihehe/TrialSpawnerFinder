package cn.minecraftfinder.geode;

public record MinecraftVersion(int major, int minor, int patch) implements Comparable<MinecraftVersion> {
    public static MinecraftVersion parse(String value) {
        String[] parts = value.trim().split("\\.");
        if (parts.length < 2 || parts.length > 3) {
            throw new IllegalArgumentException("Minecraft 版本格式必须是 major.minor 或 major.minor.patch");
        }
        try {
            return new MinecraftVersion(
                    Integer.parseInt(parts[0]),
                    Integer.parseInt(parts[1]),
                    parts.length == 3 ? Integer.parseInt(parts[2]) : 0);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Minecraft 版本格式无效: " + value, e);
        }
    }

    @Override
    public int compareTo(MinecraftVersion other) {
        int result = Integer.compare(major, other.major);
        if (result == 0) result = Integer.compare(minor, other.minor);
        if (result == 0) result = Integer.compare(patch, other.patch);
        return result;
    }

    public boolean isAtLeast(int expectedMajor, int expectedMinor, int expectedPatch) {
        return compareTo(new MinecraftVersion(expectedMajor, expectedMinor, expectedPatch)) >= 0;
    }
}
