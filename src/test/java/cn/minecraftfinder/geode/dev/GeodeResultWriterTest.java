package cn.minecraftfinder.geode.dev;

import cn.minecraftfinder.geode.GeodeCandidate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GeodeResultWriterTest {
    @TempDir
    Path directory;

    @Test
    void realResultsSortByActualCountAndWriterHonorsTheResultLimit() throws Exception {
        List<VerifiedGeodeCandidate> results = new ArrayList<>(List.of(
                verified(0, 0, 500, 20),
                verified(1, 0, 700, 10),
                verified(2, 0, 600, 30)));
        results.sort(null);
        Path output = directory.resolve("results.csv");

        GeodeResultWriter.write(output, results, 2);

        assertEquals(List.of(30, 20, 10), results.stream()
                .map(VerifiedGeodeCandidate::actualBuddingCount).toList());
        List<String> lines = Files.readAllLines(output);
        assertEquals(3, lines.size());
        assertTrue(lines.getFirst().contains("活动区块晶洞起点数"));
        assertTrue(lines.get(1).contains(";30;已验证"));
        assertTrue(lines.get(2).contains(";20;已验证"));
    }

    private static VerifiedGeodeCandidate verified(
            int chunkX, int chunkZ, int theoreticalBudding, int actualBudding) {
        return new VerifiedGeodeCandidate(
                new GeodeCandidate(chunkX, chunkZ, 5, theoreticalBudding), actualBudding);
    }
}
