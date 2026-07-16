package cn.minecraftfinder.geode.dev;

import cn.minecraftfinder.geode.GeodeCandidate;
import cn.minecraftfinder.geode.GeodeFinderConfig;
import cn.minecraftfinder.geode.GeodeSearch;
import cn.minecraftfinder.geode.MinecraftVersion;
import cn.minecraftfinder.core.ResultFiles;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.runtime.ConsoleProgressReporter;
import net.minecraft.server.MinecraftServer;

import java.nio.file.Path;
import java.util.List;

public final class GeodeDevelopmentRunner {
    private GeodeDevelopmentRunner() {
    }

    public static void run(MinecraftServer server) {
        try {
            ProgressReporter progress = new ConsoleProgressReporter();
            GeodeFinderConfig config = GeodeFinderConfig.load(Path.of("finder.properties"));
            if (!config.minecraftVersion().equals(new MinecraftVersion(1, 21, 1))) {
                throw new IllegalArgumentException(
                        "当前开发验证环境是 Minecraft 1.21.1；请设置 minecraft-version=1.21.1");
            }
            if (server.getOverworld().getSeed() != config.seed()) {
                throw new IllegalStateException("服务端世界种子与 finder.properties 不一致");
            }

            System.out.println("开始紫水晶母岩全区域粗筛和纯石头模拟精排……");
            List<GeodeCandidate> theoretical = GeodeSearch.search(config, progress);
            int verifyCount = Math.min(config.verificationLimit(), theoretical.size());
            List<GeodeCandidate> selected = theoretical.subList(0, verifyCount);
            System.out.println("纯算法候选 %,d 个，真实验证前 %,d 名。".formatted(
                    theoretical.size(), verifyCount));

            List<VerifiedGeodeCandidate> verified = new BatchMinecraftGeodeVerifier(
                    server.getOverworld(), config).verify(selected, progress);
            Path output = nextOutputPath();
            GeodeResultWriter.write(output, verified, config.resultLimit());
            System.out.println("紫水晶搜索完成：" + output.toAbsolutePath());
            System.out.println("对齐文本：" + GeodeResultWriter.textPath(output).toAbsolutePath());
        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private static Path nextOutputPath() {
        return ResultFiles.next(Path.of(".."), "geode");
    }
}
