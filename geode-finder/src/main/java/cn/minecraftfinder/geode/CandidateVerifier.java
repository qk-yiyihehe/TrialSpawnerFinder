package cn.minecraftfinder.geode;

@FunctionalInterface
public interface CandidateVerifier<C, R> {
    R verify(C candidate) throws Exception;
}
