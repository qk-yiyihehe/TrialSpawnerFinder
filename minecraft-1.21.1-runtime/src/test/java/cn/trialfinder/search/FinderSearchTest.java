package cn.trialfinder.search;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FinderSearchTest {
    @Test
    void fineThreadsLeaveTwoLogicalProcessorsForTheSystem() {
        assertEquals(1, FinderSearch.fineThreadCount(1));
        assertEquals(2, FinderSearch.fineThreadCount(4));
        assertEquals(14, FinderSearch.fineThreadCount(16));
        assertEquals(18, FinderSearch.fineThreadCount(20));
    }
}
