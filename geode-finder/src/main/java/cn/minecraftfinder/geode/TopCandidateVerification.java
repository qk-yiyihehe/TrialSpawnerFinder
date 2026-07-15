package cn.minecraftfinder.geode;

import java.util.ArrayList;
import java.util.List;

public final class TopCandidateVerification {
    private TopCandidateVerification() {
    }

    public static <C, R> List<R> verify(
            List<C> rankedCandidates, int limit, CandidateVerifier<C, R> verifier) throws Exception {
        if (limit < 0) {
            throw new IllegalArgumentException("验证数量不能小于 0");
        }
        int count = Math.min(limit, rankedCandidates.size());
        List<R> results = new ArrayList<>(count);
        for (int index = 0; index < count; index++) {
            results.add(verifier.verify(rankedCandidates.get(index)));
        }
        return List.copyOf(results);
    }
}
