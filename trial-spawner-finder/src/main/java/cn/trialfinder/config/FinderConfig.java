package cn.trialfinder.config;

import cn.minecraftfinder.core.AreaShape;
import cn.minecraftfinder.core.FinderProperties;
import cn.minecraftfinder.core.MinecraftWorld;
import cn.minecraftfinder.core.SearchArea;
import cn.minecraftfinder.core.SearchBounds;
import cn.minecraftfinder.core.ScanSettings;

import java.io.IOException;
import java.nio.file.Path;

public record FinderConfig(
        long seed,
        int searchCenterX,
        int searchCenterZ,
        int searchRadiusBlocks,
        boolean fullWorld,
        AreaShape searchAreaShape,
        int clusterRadiusBlocks,
        AreaShape areaShape,
        int minStructures,
        int minSpawners,
        int scanThreads,
        int scanShardSizeBlocks) {

    public static FinderConfig load(Path path) throws IOException {
        FinderProperties properties = FinderProperties.load(path);
        SearchArea searchArea = SearchArea.load(properties, MinecraftWorld.BLOCK_LIMIT);
        ScanSettings scan = ScanSettings.load(properties);

        FinderConfig config = new FinderConfig(
                properties.requiredLong("seed"),
                searchArea.centerX(),
                searchArea.centerZ(),
                searchArea.radiusBlocks(),
                searchArea.fullWorld(),
                searchArea.shape(),
                properties.requiredInt("cluster-radius-blocks"),
                AreaShape.parse(properties.required("area-shape")),
                properties.requiredInt("min-structures"),
                properties.requiredInt("min-spawners"),
                scan.threads(),
                scan.shardSizeBlocks());
        config.validate();
        return config;
    }

    private void validate() {
        if (clusterRadiusBlocks <= 0) {
            throw new IllegalArgumentException("聚类半径必须大于 0");
        }
        if (minStructures <= 0 || minSpawners < 0) {
            throw new IllegalArgumentException("min-structures 必须大于 0，min-spawners 不能小于 0");
        }
    }

    public SearchBounds searchBounds() {
        return searchArea().bounds();
    }

    public SearchArea searchArea() {
        return new SearchArea(
                searchCenterX, searchCenterZ, searchRadiusBlocks,
                searchAreaShape, fullWorld, MinecraftWorld.BLOCK_LIMIT);
    }

    public long searchMinX() {
        return searchBounds().minX();
    }

    public long searchMaxX() {
        return searchBounds().maxX();
    }

    public long searchMinZ() {
        return searchBounds().minZ();
    }

    public long searchMaxZ() {
        return searchBounds().maxZ();
    }

    public boolean containsSearchPoint(long x, long z) {
        return searchArea().contains(x, z);
    }

}
