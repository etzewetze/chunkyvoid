package dev.etzewetze.chunkyvoid;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class BorderBoundsTest {
    @Test
    void radiusIsInclusiveAndReturnIsTenBlocksInside() {
        BorderBounds bounds = new BorderBounds(0, 0, 500, 10, 3);
        assertFalse(bounds.outside(500, -500));
        assertTrue(bounds.outside(501, 0));
        assertTrue(bounds.outside(0, -501));
        assertEquals(490, bounds.returnX(501));
        assertEquals(-490, bounds.returnZ(-501));
    }

    @Test
    void displacedCenterAndDiagonalAreClamped() {
        BorderBounds bounds = new BorderBounds(100, -20, 500, 10, 3);
        assertTrue(bounds.outside(601, 480));
        assertEquals(590, bounds.returnX(601));
        assertEquals(470, bounds.returnZ(480));
        assertEquals(100, bounds.returnX(100));
    }
}
