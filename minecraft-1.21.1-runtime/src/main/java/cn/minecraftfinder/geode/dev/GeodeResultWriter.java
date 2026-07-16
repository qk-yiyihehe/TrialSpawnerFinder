package cn.minecraftfinder.geode.dev;

import cn.minecraftfinder.core.ResultFiles;
import cn.minecraftfinder.core.ResultTableWriter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class GeodeResultWriter {
    private static final List<String> HEADERS = List.of(
            "排名", "中心X", "中心Z", "中心区块X", "中心区块Z",
            "活动区块晶洞起点数", "纯石头模拟母岩数", "实际母岩数", "验证状态");

    private GeodeResultWriter() {
    }

    static void write(Path path, List<VerifiedGeodeCandidate> results, int limit) throws IOException {
        int count = Math.min(limit, results.size());
        List<List<String>> rows = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            VerifiedGeodeCandidate result = results.get(index);
            var candidate = result.candidate();
            rows.add(List.of(
                    Integer.toString(index + 1),
                    Long.toString((long) candidate.playerX()),
                    Long.toString((long) candidate.playerZ()),
                    Integer.toString(candidate.centerChunkX()),
                    Integer.toString(candidate.centerChunkZ()),
                    Integer.toString(candidate.geodeCount()),
                    Integer.toString(candidate.buddingCount()),
                    Integer.toString(result.actualBuddingCount()),
                    "已验证"));
        }
        ResultTableWriter.write(path, HEADERS, rows);
    }

    static Path textPath(Path csvPath) {
        return ResultFiles.textPath(csvPath);
    }
}
