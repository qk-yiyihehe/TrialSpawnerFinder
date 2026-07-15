package cn.minecraftfinder.geode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RandomTickFootprintTest {
    @Test
    void legacyRuleUsesDistanceToWholeChunkCenter() {
        LegacyRandomTickFootprint footprint = new LegacyRandomTickFootprint(128);

        assertTrue(footprint.containsChunk(0, 0, 7, 0));
        assertFalse(footprint.containsChunk(0, 0, 8, 0));
        assertTrue(footprint.containsChunk(0, 0, 5, 5));
        assertFalse(footprint.containsChunk(0, 0, 6, 6));
    }

    @Test
    void modernRuleUsesPlayerChunkAndSimulationDistance() {
        SimulationDistanceFootprint footprint = new SimulationDistanceFootprint(10);

        assertTrue(footprint.containsChunk(15.9, 15.9, 10, -10));
        assertFalse(footprint.containsChunk(15.9, 15.9, 11, 0));
        assertTrue(footprint.containsChunk(16.0, 16.0, 11, 11));
    }

    @Test
    void selectsRuleAtMinecraft1215() {
        assertInstanceOf(LegacyRandomTickFootprint.class,
                RandomTickFootprint.forVersion(MinecraftVersion.parse("1.21.4"), 128, 10));
        assertInstanceOf(SimulationDistanceFootprint.class,
                RandomTickFootprint.forVersion(MinecraftVersion.parse("1.21.5"), 128, 10));
    }
}
