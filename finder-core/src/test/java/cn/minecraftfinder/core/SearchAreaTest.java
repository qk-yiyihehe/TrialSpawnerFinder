package cn.minecraftfinder.core;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SearchAreaTest {
    @Test
    void supportsCircleAndSquareSearchAreas() {
        SearchArea circle = new SearchArea(0, 0, 10, AreaShape.CIRCLE, false, 100);
        SearchArea square = new SearchArea(0, 0, 10, AreaShape.SQUARE, false, 100);

        assertFalse(circle.contains(10, 10));
        assertTrue(square.contains(10, 10));
    }

    @Test
    void clipsAtWorldBorder() {
        SearchArea area = new SearchArea(95, 95, 20, AreaShape.SQUARE, false, 100);

        assertTrue(area.contains(100, 100));
        assertFalse(area.contains(101, 100));
        assertTrue(area.bounds().contains(75, 75));
    }
}
