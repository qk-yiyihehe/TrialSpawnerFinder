package cn.minecraftfinder.geode.dev;

import cn.minecraftfinder.geode.GeodeCandidate;
import cn.minecraftfinder.geode.GeodeFinderConfig;
import cn.minecraftfinder.geode.RandomTickFootprint;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.core.ProgressUpdate;
import net.minecraft.block.Blocks;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

final class BatchMinecraftGeodeVerifier {
    private final ServerWorld world;
    private final GeodeFinderConfig config;
    private final RandomTickFootprint footprint;

    BatchMinecraftGeodeVerifier(ServerWorld world, GeodeFinderConfig config) {
        this.world = world;
        this.config = config;
        this.footprint = config.randomTickFootprint();
    }

    List<VerifiedGeodeCandidate> verify(
            List<GeodeCandidate> candidates, ProgressReporter progress) {
        Set<Long> requiredChunks = requiredChunkKeys(candidates);

        System.out.println("真实验证需要生成 %,d 个去重区块。".formatted(requiredChunks.size()));
        Map<Long, Integer> buddingByChunk = new HashMap<>(Math.max(16, requiredChunks.size() * 2));
        int completed = 0;
        progress.report(ProgressUpdate.phase(
                "真实验证", 0, requiredChunks.size(), "区块"));
        for (long chunkKey : requiredChunks) {
            int chunkX = (int) (chunkKey >> 32);
            int chunkZ = (int) chunkKey;
            buddingByChunk.put(chunkKey, countBudding(world.getChunk(chunkX, chunkZ)));
            completed++;
            progress.report(ProgressUpdate.phase(
                    "真实验证", completed, requiredChunks.size(), "区块"));
        }

        List<VerifiedGeodeCandidate> results = new ArrayList<>(candidates.size());
        for (GeodeCandidate candidate : candidates) {
            int[] total = {0};
            visitCandidateChunks(candidate,
                    (chunkX, chunkZ) -> total[0] += buddingByChunk.getOrDefault(key(chunkX, chunkZ), 0));
            results.add(new VerifiedGeodeCandidate(candidate, total[0]));
        }
        results.sort(null);
        return List.copyOf(results);
    }

    Set<Long> requiredChunkKeys(List<GeodeCandidate> candidates) {
        Set<Long> requiredChunks = new LinkedHashSet<>();
        for (GeodeCandidate candidate : candidates) {
            visitCandidateChunks(candidate,
                    (chunkX, chunkZ) -> requiredChunks.add(key(chunkX, chunkZ)));
        }
        return requiredChunks;
    }

    private void visitCandidateChunks(GeodeCandidate candidate, ChunkVisitor visitor) {
        int radius = config.minecraftVersion().isAtLeast(1, 21, 5)
                ? config.simulationDistanceChunks()
                : Math.ceilDiv(config.legacyRandomTickRadiusBlocks(), 16);
        for (int chunkX = candidate.centerChunkX() - radius;
                chunkX <= candidate.centerChunkX() + radius; chunkX++) {
            for (int chunkZ = candidate.centerChunkZ() - radius;
                    chunkZ <= candidate.centerChunkZ() + radius; chunkZ++) {
                if (GeodeFinderConfig.isChunkInsideWorld(chunkX, chunkZ)
                        && footprint.containsChunk(candidate.playerX(), candidate.playerZ(), chunkX, chunkZ)) {
                    visitor.accept(chunkX, chunkZ);
                }
            }
        }
    }


    private static int countBudding(Chunk chunk) {
        int count = 0;
        for (ChunkSection section : chunk.getSectionArray()) {
            if (section == null || section.isEmpty()
                    || !section.hasAny(state -> state.isOf(Blocks.BUDDING_AMETHYST))) {
                continue;
            }
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        if (section.getBlockState(x, y, z).isOf(Blocks.BUDDING_AMETHYST)) count++;
                    }
                }
            }
        }
        return count;
    }

    private static long key(int chunkX, int chunkZ) {
        return ((long) chunkX << 32) ^ (chunkZ & 0xffffffffL);
    }

    @FunctionalInterface
    private interface ChunkVisitor {
        void accept(int chunkX, int chunkZ);
    }
}
