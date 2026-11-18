package dev.etzewetze.chunkyvoid;

import java.util.List;
import java.util.Random;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

/** Only used for new chunks; already saved terrain is never overwritten. */
public final class VoidGenerator extends ChunkGenerator {
    private static final BiomeProvider VOID_BIOMES = new BiomeProvider() {
        @Override
        public Biome getBiome(WorldInfo info, int x, int y, int z) {
            return Biome.THE_VOID;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo info) {
            return List.of(Biome.THE_VOID);
        }
    };

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo info) {
        return VOID_BIOMES;
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, Math.min(80, world.getMaxHeight() - 2), 0.5);
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int x, int z, BiomeGrid biome) {
        return createChunkData(world);
    }

    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateBedrock() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
}
