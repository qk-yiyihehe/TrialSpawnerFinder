package cn.minecraftfinder.geode;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.SearchArea;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public record GeodeFinderConfig(
        long seed,
        MinecraftVersion minecraftVersion,
        SearchArea searchArea,
        int legacyRandomTickRadiusBlocks,
        int simulationDistanceChunks,
        int minimumGeodes,
        int minimumBuddingAmethyst,
        int prefilterLimit,
        int verificationLimit,
        int resultLimit,
        int scanThreads,
        int scanShardSizeBlocks) {

    public static final int WORLD_LIMIT = 30_000_000;

    public GeodeFinderConfig(
            long seed, MinecraftVersion minecraftVersion, SearchArea searchArea,
            int legacyRandomTickRadiusBlocks, int simulationDistanceChunks,
            int minimumGeodes, int minimumBuddingAmethyst, int verificationLimit,
            int scanThreads, int scanShardSizeBlocks) {
        this(seed, minecraftVersion, searchArea, legacyRandomTickRadiusBlocks,
                simulationDistanceChunks, minimumGeodes, minimumBuddingAmethyst,
                5_000, verificationLimit, 20, scanThreads, scanShardSizeBlocks);
    }

    public static GeodeFinderConfig load(Path path) throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
            properties.load(reader);
        }
        boolean fullWorld = optionalBoolean(properties, "full-world", false);
        String activityRule = properties.getProperty("geode-activity-rule", "auto").trim();
        if (!activityRule.equalsIgnoreCase("auto")) {
            throw new IllegalArgumentException("geode-activity-rule 当前只能是 auto");
        }
        GeodeFinderConfig config = new GeodeFinderConfig(
                requiredLong(properties, "seed"),
                MinecraftVersion.parse(required(properties, "minecraft-version")),
                new SearchArea(
                        requiredInt(properties, "search-center-x"),
                        requiredInt(properties, "search-center-z"),
                        requiredInt(properties, "search-radius-blocks"),
                        AreaShape.parse(properties.getProperty("search-area-shape", "circle")),
                        fullWorld,
                        WORLD_LIMIT),
                optionalInt(properties, "geode-legacy-random-tick-radius-blocks", 128),
                optionalInt(properties, "geode-simulation-distance-chunks", 10),
                optionalInt(properties, "geode-min-geodes", 20),
                optionalInt(properties, "geode-min-budding-amethyst", 1),
                optionalInt(properties, "geode-prefilter-limit", 5_000),
                optionalInt(properties, "geode-verification-limit", 100),
                optionalInt(properties, "geode-result-limit", 20),
                optionalInt(properties, "scan-threads",
                        Math.min(8, Runtime.getRuntime().availableProcessors())),
                optionalInt(properties, "scan-shard-size-blocks", 262_144));
        config.validate();
        return config;
    }

    public RandomTickFootprint randomTickFootprint() {
        return RandomTickFootprint.forVersion(
                minecraftVersion, legacyRandomTickRadiusBlocks, simulationDistanceChunks);
    }

    public static boolean isChunkInsideWorld(int chunkX, int chunkZ) {
        long minX = (long) chunkX * 16;
        long minZ = (long) chunkZ * 16;
        return minX + 15 >= -WORLD_LIMIT && minX < WORLD_LIMIT
                && minZ + 15 >= -WORLD_LIMIT && minZ < WORLD_LIMIT;
    }

    private void validate() {
        if (!minecraftVersion.isAtLeast(1, 19, 0)) {
            throw new IllegalArgumentException("当前紫水晶算法仅支持 Minecraft 1.19+");
        }
        if (minimumGeodes <= 0 || minimumBuddingAmethyst < 0) {
            throw new IllegalArgumentException("晶洞阈值必须大于 0，母岩阈值不能小于 0");
        }
        if (prefilterLimit <= 0 || verificationLimit < 0 || resultLimit <= 0) {
            throw new IllegalArgumentException("粗筛数量和结果数量必须大于 0，验证数量不能小于 0");
        }
        if (scanThreads <= 0 || scanThreads > 64 || scanShardSizeBlocks <= 0) {
            throw new IllegalArgumentException("scan-threads 必须为 1..64，分片边长必须大于 0");
        }
        randomTickFootprint();
    }

    private static String required(Properties properties, String key) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("缺少配置项: " + key);
        }
        return value.trim();
    }

    private static long requiredLong(Properties properties, String key) {
        try {
            return Long.parseLong(required(properties, key));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("配置项不是有效整数: " + key, e);
        }
    }

    private static int requiredInt(Properties properties, String key) {
        return Math.toIntExact(requiredLong(properties, key));
    }

    private static int optionalInt(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        return requiredInt(properties, key);
    }

    private static boolean optionalBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) return fallback;
        if (value.trim().equalsIgnoreCase("true")) return true;
        if (value.trim().equalsIgnoreCase("false")) return false;
        throw new IllegalArgumentException("配置项只能是 true 或 false: " + key);
    }
}
