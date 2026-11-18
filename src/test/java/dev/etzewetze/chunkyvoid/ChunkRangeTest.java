package dev.etzewetze.chunkyvoid;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

class ChunkRangeTest {
    @Test
    void includesBothBoundaryChunksAndVisitsEachOnce() {
        ChunkRange range = ChunkRange.around(0, 0, 500);
        assertEquals(-32, range.minX());
        assertEquals(-32, range.minZ());
        assertEquals(64 * 64L, range.total());
        assertEquals(-32, range.xAt(0));
        assertEquals(-32, range.zAt(0));
        assertEquals(31, range.xAt(range.total() - 1));
        assertEquals(31, range.zAt(range.total() - 1));
        assertThrows(IndexOutOfBoundsException.class, () -> range.xAt(range.total()));
    }

    @Test
    void negativeCoordinatesUseFloorDivision() {
        ChunkRange range = ChunkRange.around(100, -20, 17);
        assertEquals(5, range.minX());
        assertEquals(-3, range.minZ());
        assertEquals(3, range.width());
        assertEquals(3, range.height());
    }
}
