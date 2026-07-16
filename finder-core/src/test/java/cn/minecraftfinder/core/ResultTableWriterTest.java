package cn.minecraftfinder.core;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResultTableWriterTest {
    @TempDir
    Path directory;

    @Test
    void writesCsvAndAlignedText() throws Exception {
        Path csv = directory.resolve("results.csv");
        ResultTableWriter.write(csv, List.of("排名", "数量", "位置"), List.of(
                List.of("1", "20", "0,0"),
                List.of("2", "3", "10,10")));

        assertEquals("\uFEFF排名;数量;位置", Files.readAllLines(csv).getFirst());
        Path text = ResultFiles.textPath(csv);
        assertTrue(Files.exists(text));
        assertTrue(Files.readString(text).contains(" 20"));
    }
}
