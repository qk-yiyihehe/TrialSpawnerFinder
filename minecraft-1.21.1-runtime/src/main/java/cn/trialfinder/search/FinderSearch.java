package cn.trialfinder.search;

import cn.trialfinder.config.FinderConfig;
import cn.trialfinder.io.ResultWriter;
import cn.minecraftfinder.core.BlockPoint;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.core.ProgressUpdate;
import cn.trialfinder.model.SearchResult;
import cn.trialfinder.model.SpawnerPoint;
import cn.trialfinder.model.TrialResultRanking;
import cn.trialfinder.world.TrialChamberGenerator;
import net.minecraft.server.world.ServerWorld;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public final class FinderSearch {
    private final FinderConfig config;
    private final Path output;
    private final ProgressReporter progress;
    private final List<SearchResult> results = new ArrayList<>();

    public FinderSearch(FinderConfig config, Path output, ProgressReporter progress) {
        this.config = config;
        this.output = output;
        this.progress = progress;
    }

    public void run(ServerWorld world) throws IOException {
        Instant started = Instant.now();
        System.out.println("[1/3] 多线程分片枚举并筛选聚类...");
        System.out.println("JVM 可用逻辑处理器：%d；快速扫描实际线程：%d".formatted(
                Runtime.getRuntime().availableProcessors(), config.scanThreads()));
        if (config.fullWorld()) {
            System.out.println("搜索范围：完整世界正方形（-30000000 到 30000000）");
        } else {
            System.out.println("搜索范围：圆形半径 %,d，裁剪边界 X[%d,%d] Z[%d,%d]".formatted(
                    config.searchRadiusBlocks(), config.searchMinX(), config.searchMaxX(),
                    config.searchMinZ(), config.searchMaxZ()));
        }
        System.out.println("分片边长：%,d 方块".formatted(config.scanShardSizeBlocks()));
        ShardedClusterScanner.ScanResult scan = ShardedClusterScanner.scan(config, progress);
        System.out.println("找到 %,d 个随机分布候选。".formatted(scan.candidateCount()));

        System.out.println("[2/3] 合并分片聚类...");
        List<CircleClusters.StructureCluster> clusters = scan.clusters();
        System.out.println("找到 %,d 个需要精细验证的候选聚类。".formatted(clusters.size()));

        System.out.println("[3/3] 使用 Minecraft 1.21.1 生成结构并统计试炼刷怪笼...");
        Map<BlockPoint, TrialChamberGenerator.GeneratedChamber> cache = new ConcurrentHashMap<>();
        Map<List<BlockPoint>, SearchResult> unique = new LinkedHashMap<>();
        Set<BlockPoint> requiredStructures = new TreeSet<>();
        clusters.forEach(cluster -> requiredStructures.addAll(cluster.structures()));
        int threadCount = fineThreadCount(Runtime.getRuntime().availableProcessors());
        System.out.println("精细生成实际线程：%d；唯一候选密室：%,d 座。".formatted(
                threadCount, requiredStructures.size()));
        AtomicInteger generatedCount = new AtomicInteger();
        progress.report(ProgressUpdate.phase(
                "生成", 0, requiredStructures.size(), "座"));
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<GenerationTask> tasks = new ArrayList<>(requiredStructures.size());
            for (BlockPoint point : requiredStructures) {
                Future<?> future = executor.submit(() -> {
                    TrialChamberGenerator generator = new TrialChamberGenerator(world);
                    cache.put(point, generator.generate(point));
                    progress.report(ProgressUpdate.phase(
                            "生成", generatedCount.incrementAndGet(),
                            requiredStructures.size(), "座"));
                });
                tasks.add(new GenerationTask(point, future));
            }
            executor.shutdown();
            if (!executor.awaitTermination(1, TimeUnit.HOURS)) {
                throw new IllegalStateException("试炼密室并行生成超时");
            }
            List<GenerationTask> failed = new ArrayList<>();
            for (GenerationTask task : tasks) {
                try {
                    task.future().get();
                } catch (ExecutionException e) {
                    failed.add(task);
                }
            }
            if (!failed.isEmpty()) {
                System.out.println("检测到 %d 座密室发生并发生成异常，正在单线程重试...".formatted(
                        failed.size()));
                TrialChamberGenerator generator = new TrialChamberGenerator(world);
                for (GenerationTask task : failed) {
                    try {
                        cache.put(task.point(), generator.generate(task.point()));
                        progress.report(ProgressUpdate.phase(
                                "生成", generatedCount.incrementAndGet(),
                                requiredStructures.size(), "座"));
                    } catch (RuntimeException e) {
                        throw new IllegalStateException(
                                "密室 %d,%d 串行重试仍然失败".formatted(
                                        task.point().x(), task.point().z()), e);
                    }
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("试炼密室生成被中断", e);
        }

        progress.report(ProgressUpdate.phase("统计", 0, clusters.size(), "组"));
        for (int index = 0; index < clusters.size(); index++) {
            CircleClusters.StructureCluster cluster = clusters.get(index);
            List<TrialChamberGenerator.GeneratedChamber> chambers = cluster.structures().stream()
                    .map(cache::get)
                    .filter(TrialChamberGenerator.GeneratedChamber::exists)
                    .toList();
            if (chambers.size() < config.minStructures()) {
                progress.report(ProgressUpdate.phase(
                        "统计", index + 1, clusters.size(), "组"));
                continue;
            }

            List<BlockPoint> structures = chambers.stream()
                    .map(TrialChamberGenerator.GeneratedChamber::position)
                    .sorted()
                    .toList();
            Set<SpawnerPoint> spawners = new TreeSet<>();
            chambers.forEach(chamber -> spawners.addAll(chamber.spawners()));
            ExactCenterOptimizer.CenterScore score = ExactCenterOptimizer.find(
                    config.areaShape(), config.clusterRadiusBlocks(), structures, spawners);
            long centerX = score.x();
            long centerZ = score.z();
            int spawnerCount = score.spawners();
            if (spawnerCount >= config.minSpawners()) {
                SearchResult result = new SearchResult(
                        centerX, centerZ, structures.size(), spawnerCount, structures);
                unique.merge(structures, result, FinderSearch::betterResult);
            }
            progress.report(ProgressUpdate.phase(
                    "统计", index + 1, clusters.size(), "组"));
        }

        refreshResults(unique);
        save();
        System.out.println("搜索完成：%d 个达标结果，耗时 %s。".formatted(
                results.size(), elapsed(started)));
        System.out.println("结果文件：" + output.toAbsolutePath());
        System.out.println("对齐文本：" + ResultWriter.textPath(output).toAbsolutePath());
    }

    public synchronized void save() throws IOException {
        ResultWriter.write(output, results);
    }

    private synchronized void refreshResults(Map<List<BlockPoint>, SearchResult> unique) {
        results.clear();
        results.addAll(TrialResultRanking.rank(unique.values()));
    }

    private static SearchResult betterResult(SearchResult first, SearchResult second) {
        return first.compareTo(second) <= 0 ? first : second;
    }

    static int fineThreadCount(int availableProcessors) {
        return Math.max(1, availableProcessors - 2);
    }

    private static String elapsed(Instant started) {
        Duration duration = Duration.between(started, Instant.now());
        return "%02d:%02d:%02d".formatted(
                duration.toHours(), duration.toMinutesPart(), duration.toSecondsPart());
    }

    private record GenerationTask(BlockPoint point, Future<?> future) {
    }

}
