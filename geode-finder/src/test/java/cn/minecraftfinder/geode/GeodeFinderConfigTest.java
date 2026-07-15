package cn.minecraftfinder.geode;

import cn.minecraftfinder.core.AreaShape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GeodeFinderConfigTest {
    @TempDir
    Path directory;

    @Test
    void loadsSharedSearchAreaAndModernActivityRule() throws IOException {
        Path path = write("""
                seed=123
                minecraft-version=1.21.5
                search-center-x=10
                search-center-z=-20
                search-radius-blocks=500000
                search-area-shape=square
                full-world=false
                geode-simulation-distance-chunks=15
                """);

        GeodeFinderConfig config = GeodeFinderConfig.load(path);

        assertEquals(AreaShape.SQUARE, config.searchArea().shape());
        assertEquals(15, config.simulationDistanceChunks());
        assertEquals(5_000, config.prefilterLimit());
        assertEquals(100, config.verificationLimit());
        assertEquals(20, config.resultLimit());
        assertInstanceOf(SimulationDistanceFootprint.class, config.randomTickFootprint());
    }

    @Test
    void rejectsVersionsBeforeImplementedRuleSet() throws IOException {
        Path path = write("""
                seed=0
                minecraft-version=1.18.2
                search-center-x=0
                search-center-z=0
                search-radius-blocks=100
                """);

        assertThrows(IllegalArgumentException.class, () -> GeodeFinderConfig.load(path));
    }

    @Test
    void rejectsUnknownActivityRule() throws IOException {
        Path path = write("""
                seed=0
                minecraft-version=1.21.1
                search-center-x=0
                search-center-z=0
                search-radius-blocks=100
                geode-activity-rule=legacy
                """);

        assertThrows(IllegalArgumentException.class, () -> GeodeFinderConfig.load(path));
    }

    @Test
    void loadsConfiguredCandidateLimits() throws IOException {
        Path path = write("""
                seed=0
                minecraft-version=1.21.1
                search-center-x=0
                search-center-z=0
                search-radius-blocks=100
                geode-prefilter-limit=321
                geode-verification-limit=45
                geode-result-limit=6
                """);

        GeodeFinderConfig config = GeodeFinderConfig.load(path);

        assertEquals(321, config.prefilterLimit());
        assertEquals(45, config.verificationLimit());
        assertEquals(6, config.resultLimit());
    }

    @Test
    void rejectsInvalidCandidateLimits() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> GeodeFinderConfig.load(write("""
                seed=0
                minecraft-version=1.21.1
                search-center-x=0
                search-center-z=0
                search-radius-blocks=100
                geode-prefilter-limit=0
                """)));
        assertThrows(IllegalArgumentException.class, () -> GeodeFinderConfig.load(write("""
                seed=0
                minecraft-version=1.21.1
                search-center-x=0
                search-center-z=0
                search-radius-blocks=100
                geode-verification-limit=-1
                """)));
        assertThrows(IllegalArgumentException.class, () -> GeodeFinderConfig.load(write("""
                seed=0
                minecraft-version=1.21.1
                search-center-x=0
                search-center-z=0
                search-radius-blocks=100
                geode-result-limit=0
                """)));
    }

    @Test
    void worldChunkBoundaryIncludesTheMostNegativeValidChunk() {
        assertEquals(true, GeodeFinderConfig.isChunkInsideWorld(-1_875_000, 0));
        assertEquals(false, GeodeFinderConfig.isChunkInsideWorld(-1_875_001, 0));
        assertEquals(true, GeodeFinderConfig.isChunkInsideWorld(1_874_999, 0));
        assertEquals(false, GeodeFinderConfig.isChunkInsideWorld(1_875_000, 0));
    }

    private Path write(String content) throws IOException {
        Path path = directory.resolve("finder.properties");
        Files.writeString(path, content);
        return path;
    }
}
