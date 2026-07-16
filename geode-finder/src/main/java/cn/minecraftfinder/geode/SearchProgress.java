package cn.minecraftfinder.geode;

@FunctionalInterface
public interface SearchProgress {
    SearchProgress NONE = (phase, completed, total, unit) -> { };

    void report(String phase, long completed, long total, String unit);
}
