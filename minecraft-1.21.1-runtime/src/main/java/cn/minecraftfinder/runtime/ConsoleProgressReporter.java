package cn.minecraftfinder.runtime;

import cn.minecraftfinder.core.ProgressFormatter;
import cn.minecraftfinder.core.ProgressReporter;
import cn.minecraftfinder.core.ProgressUpdate;

public final class ConsoleProgressReporter implements ProgressReporter {
    private String activePhase = "";
    private long phaseStartedNanos;
    private int nextPercent = 1;

    @Override
    public synchronized void report(ProgressUpdate update) {
        if (update.total() == 0) return;
        if (!update.phase().equals(activePhase)) {
            activePhase = update.phase();
            phaseStartedNanos = System.nanoTime();
            nextPercent = 1;
        }
        int percent = (int) (update.completed() * 100 / update.total());
        if (update.completed() != update.total() && percent < nextPercent) return;

        long elapsedNanos = System.nanoTime() - phaseStartedNanos;
        String line = update.hasEstimatedWork()
                ? ProgressFormatter.estimatedWork(
                        update.phase(), update.completed(), update.total(),
                        update.processed(), update.estimatedWork(), update.unit(), elapsedNanos)
                : ProgressFormatter.phase(
                        update.phase(), update.completed(), update.total(),
                        update.unit(), elapsedNanos);
        System.out.println(line);
        nextPercent = percent + 1;
    }
}
