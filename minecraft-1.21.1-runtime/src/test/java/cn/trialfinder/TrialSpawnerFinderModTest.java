package cn.trialfinder;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TrialSpawnerFinderModTest {
    @TempDir
    Path directory;

    @Test
    void finderTypeDefaultsToTrialSpawnerAndAcceptsGeode() throws Exception {
        Path config = directory.resolve("finder.properties");
        Files.writeString(config, "seed=0\n");
        assertEquals("trial-spawner", TrialSpawnerFinderMod.selectedFinder(config));

        Files.writeString(config, "finder-type=geode\n");
        assertEquals("geode", TrialSpawnerFinderMod.selectedFinder(config));
    }

    @Test
    void finderTypeRejectsUnknownValues() throws Exception {
        Path config = directory.resolve("finder.properties");
        Files.writeString(config, "finder-type=unknown\n");

        assertThrows(IllegalArgumentException.class,
                () -> TrialSpawnerFinderMod.selectedFinder(config));
    }
}
