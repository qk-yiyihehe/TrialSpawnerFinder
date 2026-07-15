package cn.minecraftfinder.geode;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopCandidateVerificationTest {
    @Test
    void verifiesOnlyRequestedTopCandidates() throws Exception {
        List<Integer> verified = TopCandidateVerification.verify(
                List.of(100, 90, 80, 70), 2, score -> score + 1);

        assertEquals(List.of(101, 91), verified);
    }
}
