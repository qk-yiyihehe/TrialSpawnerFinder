package cn.minecraftfinder.geode.dev;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

final class GeodeResultWriter {
    private GeodeResultWriter() {
    }

    static void write(Path path, List<VerifiedGeodeCandidate> results, int limit) throws IOException {
        int count = Math.min(limit, results.size());
        try (BufferedWriter writer = Files.newBufferedWriter(path, StandardCharsets.UTF_8)) {
            writer.write('\uFEFF');
            writer.write("排名;中心X;中心Z;中心区块X;中心区块Z;活动区块晶洞起点数;纯石头模拟母岩数;实际母岩数;验证状态");
            writer.newLine();
            for (int index = 0; index < count; index++) {
                VerifiedGeodeCandidate result = results.get(index);
                var candidate = result.candidate();
                writer.write("%d;%d;%d;%d;%d;%d;%d;%d;已验证".formatted(
                        index + 1, (long) candidate.playerX(), (long) candidate.playerZ(),
                        candidate.centerChunkX(), candidate.centerChunkZ(), candidate.geodeCount(),
                        candidate.buddingCount(), result.actualBuddingCount()));
                writer.newLine();
            }
        }
    }
}
