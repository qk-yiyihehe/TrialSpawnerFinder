package cn.minecraftfinder.geode;

import java.util.List;

public record GeodeSimulation(
        int originX,
        int originY,
        int originZ,
        List<BuddingAmethyst> buddingAmethyst) {

    public GeodeSimulation {
        buddingAmethyst = List.copyOf(buddingAmethyst);
    }

    public int buddingCount() {
        return buddingAmethyst.size();
    }
}
