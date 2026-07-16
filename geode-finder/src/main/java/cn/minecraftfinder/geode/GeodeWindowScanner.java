package cn.minecraftfinder.geode;

import cn.minecraftfinder.core.SearchBounds;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.PriorityQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

final class GeodeWindowScanner {
    private GeodeWindowScanner() {
    }

    static List<GeodeCandidate> scan(GeodeFinderConfig config, int windowRadiusChunks) {
        return scan(config, windowRadiusChunks, SearchProgress.NONE);
    }

    static List<GeodeCandidate> scan(
            GeodeFinderConfig config, int windowRadiusChunks, SearchProgress progress) {
        SearchBounds bounds = config.searchArea().bounds();
        int centerMinX = Math.floorDiv(Math.toIntExact(bounds.minX()), 16);
        int centerMaxX = Math.floorDiv(Math.toIntExact(bounds.maxX()), 16);
        int centerMinZ = Math.floorDiv(Math.toIntExact(bounds.minZ()), 16);
        int centerMaxZ = Math.floorDiv(Math.toIntExact(bounds.maxZ()), 16);
        int height = centerMaxZ - centerMinZ + 1;
        int shardHeight = Math.max(1, Math.ceilDiv(config.scanShardSizeBlocks(), 16));
        int sliceCount = Math.ceilDiv(height, shardHeight);
        if (sliceCount == 1) {
            return scanSlice(config, windowRadiusChunks,
                    centerMinX, centerMaxX, centerMinZ, centerMaxZ, progress);
        }

        ExecutorService executor = Executors.newFixedThreadPool(
                Math.min(config.scanThreads(), sliceCount));
        List<Future<List<GeodeCandidate>>> futures = new ArrayList<>(sliceCount);
        try {
            for (int index = 0; index < sliceCount; index++) {
                int sliceMinZ = centerMinZ + index * shardHeight;
                int sliceMaxZ = Math.min(centerMaxZ, sliceMinZ + shardHeight - 1);
                futures.add(executor.submit(() -> scanSlice(config, windowRadiusChunks,
                        centerMinX, centerMaxX, sliceMinZ, sliceMaxZ, SearchProgress.NONE)));
            }
            PriorityQueue<GeodeCandidate> candidates = candidateQueue(config.prefilterLimit());
            for (int index = 0; index < futures.size(); index++) {
                Future<List<GeodeCandidate>> future = futures.get(index);
                for (GeodeCandidate candidate : future.get()) {
                    offer(candidates, candidate, config.prefilterLimit());
                }
                progress.report("粗筛", index + 1, sliceCount, "分片");
            }
            return sorted(candidates);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("紫水晶扫描被中断", e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("紫水晶扫描分片失败", e.getCause());
        } finally {
            executor.shutdownNow();
        }
    }

    private static List<GeodeCandidate> scanSlice(
            GeodeFinderConfig config,
            int windowRadiusChunks,
            int centerMinX,
            int centerMaxX,
            int centerMinZ,
            int centerMaxZ,
            SearchProgress progress) {
        int sampleMinX = centerMinX - windowRadiusChunks;
        int sampleMaxX = centerMaxX + windowRadiusChunks;
        int sampleMinZ = centerMinZ - windowRadiusChunks;
        int sampleMaxZ = centerMaxZ + windowRadiusChunks;
        int width = Math.addExact(Math.subtractExact(sampleMaxX, sampleMinX), 1);
        int diameter = windowRadiusChunks * 2 + 1;
        byte[] history = new byte[Math.multiplyExact(width, diameter)];
        int[] columnSums = new int[width];
        PriorityQueue<GeodeCandidate> candidates = candidateQueue(config.prefilterLimit());
        ModernGeodeSimulator simulator = new ModernGeodeSimulator(config.seed());
        int totalRows = sampleMaxZ - sampleMinZ + 1;
        int reportStep = Math.max(1, totalRows / 100);

        for (int z = sampleMinZ; z <= sampleMaxZ; z++) {
            int row = Math.floorMod(z - sampleMinZ, diameter) * width;
            int windowSum = 0;
            for (int index = 0; index < width; index++) {
                int x = sampleMinX + index;
                int old = history[row + index];
                int value = GeodeFinderConfig.isChunkInsideWorld(x, z)
                        && simulator.isGeodeChunk(x, z) ? 1 : 0;
                columnSums[index] += value - old;
                history[row + index] = (byte) value;
                windowSum += columnSums[index];
                if (index >= diameter) windowSum -= columnSums[index - diameter];
                if (index < diameter - 1 || z < sampleMinZ + diameter - 1) continue;

                int centerX = x - windowRadiusChunks;
                int centerZ = z - windowRadiusChunks;
                if (centerX < centerMinX || centerX > centerMaxX
                        || centerZ < centerMinZ || centerZ > centerMaxZ
                        || windowSum < config.minimumGeodes()) {
                    continue;
                }
                long blockX = centerX * 16L + 8L;
                long blockZ = centerZ * 16L + 8L;
                if (!config.searchArea().contains(blockX, blockZ)) continue;

                offer(candidates, new GeodeCandidate(centerX, centerZ, windowSum, 0),
                        config.prefilterLimit());
            }
            int completedRows = z - sampleMinZ + 1;
            if (completedRows == totalRows || completedRows % reportStep == 0) {
                progress.report("粗筛", completedRows, totalRows, "区块行");
            }
        }
        return sorted(candidates);
    }

    private static PriorityQueue<GeodeCandidate> candidateQueue(int limit) {
        return new PriorityQueue<>(Math.min(limit, 1024),
                Comparator.comparingInt(GeodeCandidate::geodeCount)
                        .thenComparing(Comparator.comparingInt(GeodeCandidate::centerChunkX).reversed())
                        .thenComparing(Comparator.comparingInt(GeodeCandidate::centerChunkZ).reversed()));
    }

    private static void offer(
            PriorityQueue<GeodeCandidate> candidates, GeodeCandidate candidate, int limit) {
        if (candidates.size() < limit) {
            candidates.add(candidate);
        } else if (candidateQueueOrder(candidate, candidates.peek()) > 0) {
            candidates.poll();
            candidates.add(candidate);
        }
    }

    private static int candidateQueueOrder(GeodeCandidate first, GeodeCandidate second) {
        int result = Integer.compare(first.geodeCount(), second.geodeCount());
        if (result == 0) result = Integer.compare(second.centerChunkX(), first.centerChunkX());
        if (result == 0) result = Integer.compare(second.centerChunkZ(), first.centerChunkZ());
        return result;
    }

    private static List<GeodeCandidate> sorted(Iterable<GeodeCandidate> candidates) {
        List<GeodeCandidate> result = new ArrayList<>();
        candidates.forEach(result::add);
        result.sort(Comparator.comparingInt(GeodeCandidate::geodeCount).reversed()
                .thenComparingInt(GeodeCandidate::centerChunkX)
                .thenComparingInt(GeodeCandidate::centerChunkZ));
        return List.copyOf(result);
    }

}
