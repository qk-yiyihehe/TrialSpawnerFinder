package cn.minecraftfinder.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MinecraftFindersModTest {
    @TempDir
    Path directory;

    @Test
    void finderTypeDefaultsToTrialSpawnerAndAcceptsGeode() throws Exception {
        Path config = directory.resolve("finder.properties");
        Files.writeString(config, "seed=0\n");
        assertEquals("trial-spawner", MinecraftFindersMod.selectedFinder(config));

        Files.writeString(config, "finder-type=geode\n");
        assertEquals("geode", MinecraftFindersMod.selectedFinder(config));
    }

    @Test
    void finderTypeRejectsUnknownValues() throws Exception {
        Path config = directory.resolve("finder.properties");
        Files.writeString(config, "finder-type=unknown\n");

        assertThrows(IllegalArgumentException.class,
                () -> MinecraftFindersMod.selectedFinder(config));
    }
}
