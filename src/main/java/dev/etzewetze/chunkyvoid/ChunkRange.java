package dev.etzewetze.chunkyvoid;

/** Chunk coordinates intersecting an inclusive square of block coordinates. */
public record ChunkRange(int minX, int minZ, int width, int height) {
    public static ChunkRange around(int centerX, int centerZ, int radius) {
        if (radius < 1 || radius > 30_000_000) {
            throw new IllegalArgumentException("Radius must be between 1 and 30000000");
        }
        long minX = Math.floorDiv((long) centerX - radius, 16);
        long maxX = Math.floorDiv((long) centerX + radius, 16);
        long minZ = Math.floorDiv((long) centerZ - radius, 16);
        long maxZ = Math.floorDiv((long) centerZ + radius, 16);
        return new ChunkRange(Math.toIntExact(minX), Math.toIntExact(minZ),
                Math.toIntExact(maxX - minX + 1), Math.toIntExact(maxZ - minZ + 1));
    }

    public long total() {
        return (long) width * height;
    }

    public int xAt(long index) {
        check(index);
        return minX + (int) (index % width);
    }

    public int zAt(long index) {
        check(index);
        return minZ + (int) (index / width);
    }

    private void check(long index) {
        if (index < 0 || index >= total()) {
            throw new IndexOutOfBoundsException("Chunk index " + index);
        }
    }
}
