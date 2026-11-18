package dev.etzewetze.chunkyvoid;

import java.util.HashMap;
import java.util.Map;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.plugin.java.JavaPlugin;

public final class ChunkyVoidPlugin extends JavaPlugin {
    private volatile Map<String, BorderBounds> borders = Map.of();
    private final VoidGenerator voidGenerator = new VoidGenerator();
    private PregenerationManager pregeneration;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        readBorders();
        RuntimeBridge runtime = new RuntimeBridge(this);
        pregeneration = new PregenerationManager(this, runtime);
        getServer().getPluginManager().registerEvents(new BorderListener(this, runtime), this);
        ChunkyVoidCommand command = new ChunkyVoidCommand(this, pregeneration);
        getCommand("chunkyvoid").setExecutor(command);
        getCommand("chunkyvoid").setTabCompleter(command);
        getLogger().info("Enabled; runtime=" + (runtime.isFolia() ? "Folia" : "Bukkit/Paper"));
    }

    @Override
    public void onDisable() {
        if (pregeneration != null) pregeneration.shutdown();
    }

    @Override
    public ChunkGenerator getDefaultWorldGenerator(String worldName, String id) {
        return voidGenerator;
    }

    public BorderBounds border(String worldName) {
        return borders.get(worldName);
    }

    public synchronized void reloadBorders() {
        reloadConfig();
        readBorders();
    }

    public synchronized void setBorder(String worldName, int radius) {
        String path = "worlds." + worldName + ".border.";
        getConfig().set(path + "radius", radius);
        getConfig().set(path + "enabled", true);
        saveConfig();
        readBorders();
    }

    public synchronized void setCenter(String worldName, int x, int z) {
        String path = "worlds." + worldName + ".border.";
        getConfig().set(path + "center-x", x);
        getConfig().set(path + "center-z", z);
        saveConfig();
        readBorders();
    }

    public synchronized void disableBorder(String worldName) {
        getConfig().set("worlds." + worldName + ".border.enabled", false);
        saveConfig();
        readBorders();
    }

    private void readBorders() {
        Map<String, BorderBounds> updated = new HashMap<>();
        ConfigurationSection section = getConfig().getConfigurationSection("worlds");
        if (section == null) {
            borders = Map.of();
            return;
        }
        for (String world : section.getKeys(false)) {
            String path = "worlds." + world + ".border.";
            if (!getConfig().getBoolean(path + "enabled")) continue;
            try {
                BorderBounds bounds = new BorderBounds(getConfig().getInt(path + "center-x"),
                        getConfig().getInt(path + "center-z"), getConfig().getInt(path + "radius"),
                        getConfig().getInt(path + "setback", 10),
                        getConfig().getInt(path + "search-radius", 3));
                updated.put(world, bounds);
            } catch (IllegalArgumentException error) {
                getLogger().warning("Border disabled for " + world + ": " + error.getMessage());
            }
        }
        borders = Map.copyOf(updated);
    }
}
