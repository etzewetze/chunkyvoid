package dev.etzewetze.chunkyvoid;

/** Inclusive block-coordinate square; a radius of 500 rejects block 501. */
public record BorderBounds(int centerX, int centerZ, int radius, int setback, int searchRadius) {
    public BorderBounds {
        if (radius <= 0 || radius > 30_000_000 || setback < 1 || setback >= radius
                || searchRadius < 0 || searchRadius > 8) {
            throw new IllegalArgumentException("Invalid border radius, setback or search radius");
        }
    }

    public boolean outside(int x, int z) {
        return Math.abs((long) x - centerX) > radius || Math.abs((long) z - centerZ) > radius;
    }

    public boolean inside(int x, int z) {
        return !outside(x, z);
    }

    public int returnX(int x) {
        return clamp(x, (long) centerX - radius + setback, (long) centerX + radius - setback);
    }

    public int returnZ(int z) {
        return clamp(z, (long) centerZ - radius + setback, (long) centerZ + radius - setback);
    }

    private static int clamp(int value, long min, long max) {
        return (int) Math.max(min, Math.min(max, value));
    }
}
