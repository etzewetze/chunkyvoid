package dev.etzewetze.chunkyvoid;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/** Isolates Folia/Paper-only methods so the jar also links on plain Spigot 1.19. */
public final class RuntimeBridge {
    private final Plugin plugin;
    private final boolean folia;

    public RuntimeBridge(Plugin plugin) {
        this.plugin = plugin;
        boolean present;
        try {
            Class.forName("io.papermc.paper.threadedregions.RegionizedServer");
            present = true;
        } catch (ClassNotFoundException ignored) {
            present = false;
        }
        folia = present;
    }

    public boolean isFolia() {
        return folia;
    }

    public void atChunk(World world, int chunkX, int chunkZ, Runnable action) {
        if (!folia) {
            Bukkit.getScheduler().runTaskLater(plugin, action, 1L);
            return;
        }
        try {
            Object scheduler = Bukkit.getServer().getClass().getMethod("getRegionScheduler")
                    .invoke(Bukkit.getServer());
            scheduler.getClass().getMethod("execute", Plugin.class, World.class,
                    int.class, int.class, Runnable.class)
                    .invoke(scheduler, plugin, world, chunkX, chunkZ, action);
        } catch (ReflectiveOperationException error) {
            throw new IllegalStateException("Folia region scheduler unavailable", error);
        }
    }

    public void atBlock(World world, int x, int z, Runnable action) {
        atChunk(world, Math.floorDiv(x, 16), Math.floorDiv(z, 16), action);
    }

    public void forPlayer(Player player, Runnable action, Runnable retired) {
        if (!folia) {
            Bukkit.getScheduler().runTask(plugin, action);
            return;
        }
        try {
            Object scheduler = player.getClass().getMethod("getScheduler").invoke(player);
            boolean queued = (boolean) scheduler.getClass()
                    .getMethod("execute", Plugin.class, Runnable.class, Runnable.class, long.class)
                    .invoke(scheduler, plugin, action, retired, 1L);
            if (!queued) retired.run();
        } catch (ReflectiveOperationException error) {
            retired.run();
            throw new IllegalStateException("Folia entity scheduler unavailable", error);
        }
    }

    public void teleport(Player player, Location destination, Runnable complete) {
        if (!folia) {
            try {
                player.teleport(destination);
            } finally {
                complete.run();
            }
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            CompletableFuture<Boolean> future = (CompletableFuture<Boolean>) player.getClass()
                    .getMethod("teleportAsync", Location.class).invoke(player, destination);
            future.whenComplete((success, failure) -> {
                if (failure != null) plugin.getLogger().warning("Border teleport failed: " + failure);
                complete.run();
            });
        } catch (ReflectiveOperationException error) {
            complete.run();
            throw new IllegalStateException("Folia async teleport unavailable", error);
        }
    }

    /** One outstanding chunk request at a time. Paper uses non-urgent async loads. */
    public void requestChunk(World world, int x, int z, Consumer<Throwable> complete) {
        try {
            Method async = world.getClass().getMethod("getChunkAtAsync", int.class, int.class, boolean.class);
            CompletableFuture<?> future = (CompletableFuture<?>) async.invoke(world, x, z, true);
            future.whenComplete((chunk, failure) -> complete.accept(failure));
        } catch (NoSuchMethodException ignored) {
            try {
                world.getChunkAt(x, z);
                complete.accept(null);
            } catch (Throwable error) {
                complete.accept(error);
            }
        } catch (InvocationTargetException error) {
            complete.accept(error.getCause());
        } catch (ReflectiveOperationException error) {
            complete.accept(error);
        }
    }
}
