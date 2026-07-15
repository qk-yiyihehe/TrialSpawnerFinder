package cn.minecraftfinder.geode.dev;

import cn.minecraftfinder.geode.GeodeCandidate;

record VerifiedGeodeCandidate(GeodeCandidate candidate, int actualBuddingCount)
        implements Comparable<VerifiedGeodeCandidate> {
    @Override
    public int compareTo(VerifiedGeodeCandidate other) {
        int result = Integer.compare(other.actualBuddingCount, actualBuddingCount);
        return result != 0 ? result : candidate.compareTo(other.candidate);
    }
}
