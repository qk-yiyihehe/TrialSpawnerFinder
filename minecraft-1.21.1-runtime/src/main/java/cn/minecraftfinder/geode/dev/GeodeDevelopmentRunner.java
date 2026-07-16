package cn.minecraftfinder.geode.dev;

import cn.minecraftfinder.geode.GeodeCandidate;
import cn.minecraftfinder.geode.GeodeFinderConfig;
import cn.minecraftfinder.geode.GeodeSearch;
import cn.minecraftfinder.geode.MinecraftVersion;
import cn.minecraftfinder.core.ResultFiles;
import cn.minecraftfinder.core.ProgressFormatter;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public final class GeodeDevelopmentRunner {
    private GeodeDevelopmentRunner() {
    }

    public static void run(MinecraftServer server) {
        Path failurePath = Path.of("search.failed");
        try {
            Files.deleteIfExists(failurePath);
            GeodeFinderConfig config = GeodeFinderConfig.load(Path.of("finder.properties"));
            if (!config.minecraftVersion().equals(new MinecraftVersion(1, 21, 1))) {
                throw new IllegalArgumentException(
                        "当前开发验证环境是 Minecraft 1.21.1；请设置 minecraft-version=1.21.1");
            }
            if (server.getOverworld().getSeed() != config.seed()) {
                throw new IllegalStateException("服务端世界种子与 finder.properties 不一致");
            }

            System.out.println("开始紫水晶母岩全区域粗筛和纯石头模拟精排……");
            long[] phaseStartedNanos = {System.nanoTime()};
            String[] activePhase = {""};
            int[] nextPercent = {1};
            List<GeodeCandidate> theoretical = GeodeSearch.search(config,
                    (phase, completed, total, unit) -> {
                        if (total == 0) return;
                        if (!phase.equals(activePhase[0])) {
                            activePhase[0] = phase;
                            phaseStartedNanos[0] = System.nanoTime();
                            nextPercent[0] = 1;
                        }
                        int percent = (int) (completed * 100 / total);
                        if (completed == total || percent >= nextPercent[0]) {
                            System.out.println(ProgressFormatter.phase(
                                    phase, completed, total, unit,
                                    System.nanoTime() - phaseStartedNanos[0]));
                            nextPercent[0] = percent + 1;
                        }
                    });
            int verifyCount = Math.min(config.verificationLimit(), theoretical.size());
            List<GeodeCandidate> selected = theoretical.subList(0, verifyCount);
            System.out.println("纯算法候选 %,d 个，真实验证前 %,d 名。".formatted(
                    theoretical.size(), verifyCount));

            List<VerifiedGeodeCandidate> verified = new BatchMinecraftGeodeVerifier(
                    server.getOverworld(), config).verify(selected);
            Path output = nextOutputPath();
            GeodeResultWriter.write(output, verified, config.resultLimit());
            System.out.println("紫水晶搜索完成：" + output.toAbsolutePath());
            System.out.println("对齐文本：" + GeodeResultWriter.textPath(output).toAbsolutePath());
        } catch (Exception e) {
            System.err.println("紫水晶搜索失败：" + e.getMessage());
            e.printStackTrace(System.err);
            try {
                Files.writeString(failurePath, e.toString(), StandardCharsets.UTF_8);
            } catch (Exception markerError) {
                System.err.println("写入失败标记失败：" + markerError.getMessage());
            }
        } finally {
            server.stop(false);
        }
    }

    private static Path nextOutputPath() {
        return ResultFiles.next(Path.of(".."), "geode");
    }
}
