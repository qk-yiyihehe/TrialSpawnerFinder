package cn.minecraftfinder.geode;

import cn.minecraftfinder.core.FinderProperties;
import cn.minecraftfinder.core.MinecraftWorld;
import cn.minecraftfinder.core.ScanSettings;
import cn.minecraftfinder.core.SearchArea;

import java.io.IOException;
import java.nio.file.Path;

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
        FinderProperties properties = FinderProperties.load(path);
        SearchArea searchArea = SearchArea.load(properties, MinecraftWorld.BLOCK_LIMIT);
        ScanSettings scan = ScanSettings.load(properties);
        String activityRule = properties.optional("geode-activity-rule", "auto");
        if (!activityRule.equalsIgnoreCase("auto")) {
            throw new IllegalArgumentException("geode-activity-rule 当前只能是 auto");
        }
        GeodeFinderConfig config = new GeodeFinderConfig(
                properties.requiredLong("seed"),
                MinecraftVersion.parse(properties.required("minecraft-version")),
                searchArea,
                properties.optionalInt("geode-legacy-random-tick-radius-blocks", 128),
                properties.optionalInt("geode-simulation-distance-chunks", 10),
                properties.optionalInt("geode-min-geodes", 20),
                properties.optionalInt("geode-min-budding-amethyst", 1),
                properties.optionalInt("geode-prefilter-limit", 5_000),
                properties.optionalInt("geode-verification-limit", 100),
                properties.optionalInt("geode-result-limit", 20),
                scan.threads(),
                scan.shardSizeBlocks());
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
        return minX + 15 >= -MinecraftWorld.BLOCK_LIMIT && minX < MinecraftWorld.BLOCK_LIMIT
                && minZ + 15 >= -MinecraftWorld.BLOCK_LIMIT && minZ < MinecraftWorld.BLOCK_LIMIT;
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
        randomTickFootprint();
    }

}
