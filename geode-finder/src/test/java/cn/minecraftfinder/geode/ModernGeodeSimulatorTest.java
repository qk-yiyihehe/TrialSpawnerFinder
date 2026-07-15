package cn.minecraftfinder.geode;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ModernGeodeSimulatorTest {
    @Test
    void matchesReferenceVectorsNearOrigin() {
        assertEquals(new Counts(37, 1166), generateAround(0));
    }

    @Test
    void matchesReferenceVectorsNearWorldBorder() {
        assertEquals(new Counts(39, 1521), generateAround(1_875_000));
    }

    @Test
    void returnsNullForNonGeodeChunk() {
        ModernGeodeSimulator simulator = new ModernGeodeSimulator(0);
        assertNull(simulator.simulate(0, 0));
    }

    private static Counts generateAround(int center) {
        ModernGeodeSimulator simulator = new ModernGeodeSimulator(0);
        int geodes = 0;
        int budding = 0;
        for (int chunkX = center - 16; chunkX <= center + 16; chunkX++) {
            for (int chunkZ = center - 16; chunkZ <= center + 16; chunkZ++) {
                if (simulator.isGeodeChunk(chunkX, chunkZ)) geodes++;
                GeodeSimulation simulation = simulator.simulate(chunkX, chunkZ);
                if (simulation != null) budding += simulation.buddingCount();
            }
        }
        return new Counts(geodes, budding);
    }

    private record Counts(int geodes, int budding) {
    }
}
