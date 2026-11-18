package dev.etzewetze.chunkyvoid;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;

public final class BorderListener implements Listener {
    private final ChunkyVoidPlugin plugin;
    private final RuntimeBridge runtime;
    private final Set<UUID> pending = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<UUID, Long> lastAttempt = new ConcurrentHashMap<>();

    public BorderListener(ChunkyVoidPlugin plugin, RuntimeBridge runtime) {
        this.plugin = plugin;
        this.runtime = runtime;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || event.getFrom().getWorld() != to.getWorld()) return;
        if (event.getFrom().getBlockX() == to.getBlockX()
                && event.getFrom().getBlockZ() == to.getBlockZ()) return;
        BorderBounds bounds = plugin.border(to.getWorld().getName());
        if (bounds == null || !bounds.outside(to.getBlockX(), to.getBlockZ())) return;
        event.setCancelled(true);
        reject(event.getPlayer(), to, bounds);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        Location to = event.getTo();
        if (to == null || to.getWorld() == null || event.getFrom().getWorld() == to.getWorld()) return;
        BorderBounds bounds = plugin.border(to.getWorld().getName());
        if (bounds != null && bounds.outside(to.getBlockX(), to.getBlockZ())) {
            event.setCancelled(true);
            event.getPlayer().sendTitle(ChatColor.RED + "Du kannst hier nicht weiter.", "", 5, 40, 10);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        runtime.forPlayer(player, () -> {
            Location location = player.getLocation();
            BorderBounds bounds = plugin.border(location.getWorld().getName());
            if (bounds != null && bounds.outside(location.getBlockX(), location.getBlockZ())) {
                reject(player, location, bounds);
            }
        }, () -> pending.remove(player.getUniqueId()));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        pending.remove(event.getPlayer().getUniqueId());
        lastAttempt.remove(event.getPlayer().getUniqueId());
    }

    private void reject(Player player, Location attempted, BorderBounds bounds) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();
        Long previous = lastAttempt.get(id);
        if (previous != null && now - previous < 1500) return;
        lastAttempt.put(id, now);
        player.sendTitle(ChatColor.RED + "Du kannst hier nicht weiter.", "", 5, 40, 10);
        if (!pending.add(id)) return;

        int targetX = bounds.returnX(attempted.getBlockX());
        int targetZ = bounds.returnZ(attempted.getBlockZ());
        List<int[]> candidates = candidates(targetX, targetZ, bounds);
        if (runtime.isFolia()) {
            searchNext(player, attempted, candidates, 0, id);
        } else {
            Location landing = null;
            for (int[] candidate : candidates) {
                landing = safeAt(attempted.getWorld(), candidate[0], candidate[1], attempted);
                if (landing != null) break;
            }
            finish(player, landing, id);
        }
    }

    private List<int[]> candidates(int x, int z, BorderBounds bounds) {
        List<int[]> result = new ArrayList<>();
        int radius = bounds.searchRadius();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (bounds.inside(x + dx, z + dz)) result.add(new int[]{x + dx, z + dz, dx, dz});
            }
        }
        result.sort(Comparator.comparingInt(p -> p[2] * p[2] + p[3] * p[3]));
        return result;
    }

    private void searchNext(Player player, Location attempted, List<int[]> positions, int index, UUID id) {
        if (index >= positions.size() || !pending.contains(id)) {
            pending.remove(id);
            return;
        }
        int[] position = positions.get(index);
        try {
            runtime.atBlock(attempted.getWorld(), position[0], position[1], () -> {
                Location landing = safeAt(attempted.getWorld(), position[0], position[1], attempted);
                if (landing == null) searchNext(player, attempted, positions, index + 1, id);
                else finish(player, landing, id);
            });
        } catch (RuntimeException error) {
            pending.remove(id);
            plugin.getLogger().warning("Safe landing lookup failed: " + error);
        }
    }

    private Location safeAt(World world, int x, int z, Location facing) {
        Block ground = world.getHighestBlockAt(x, z);
        Material type = ground.getType();
        if (!type.isSolid() || type == Material.MAGMA_BLOCK || type == Material.CACTUS
                || type == Material.CAMPFIRE || type == Material.SOUL_CAMPFIRE) return null;
        int feetY = ground.getY() + 1;
        if (feetY + 1 >= world.getMaxHeight()) return null;
        if (!world.getBlockAt(x, feetY, z).getType().isAir()
                || !world.getBlockAt(x, feetY + 1, z).getType().isAir()) return null;
        return new Location(world, x + 0.5, feetY, z + 0.5, facing.getYaw(), facing.getPitch());
    }

    private void finish(Player player, Location landing, UUID id) {
        if (landing == null) {
            pending.remove(id); // No solid block in a true void: movement remains cancelled.
            return;
        }
        try {
            runtime.forPlayer(player, () -> {
                if (!player.isOnline() || player.getWorld() != landing.getWorld()) {
                    pending.remove(id);
                    return;
                }
                runtime.teleport(player, landing, () -> pending.remove(id));
            }, () -> pending.remove(id));
        } catch (RuntimeException error) {
            pending.remove(id);
            plugin.getLogger().warning("Safe border teleport failed: " + error);
        }
    }
}
