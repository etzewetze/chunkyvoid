package dev.etzewetze.chunkyvoid;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

public final class ChunkyVoidCommand implements CommandExecutor, TabCompleter {
    private final ChunkyVoidPlugin plugin;
    private final PregenerationManager pregeneration;

    public ChunkyVoidCommand(ChunkyVoidPlugin plugin, PregenerationManager pregeneration) {
        this.plugin = plugin;
        this.pregeneration = pregeneration;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("chunkyvoid.admin")) {
            sender.sendMessage("Keine Berechtigung.");
            return true;
        }
        if (args.length == 0) return help(sender);
        try {
            switch (args[0].toLowerCase()) {
                case "reload" -> {
                    plugin.reloadBorders();
                    sender.sendMessage("ChunkyVoid-Konfiguration geladen.");
                }
                case "border" -> border(sender, args);
                case "pregen" -> pregen(sender, args);
                default -> help(sender);
            }
        } catch (NumberFormatException error) {
            sender.sendMessage("Bitte ganze Zahlen eingeben.");
        } catch (IllegalArgumentException error) {
            sender.sendMessage(error.getMessage());
        }
        return true;
    }

    private void border(CommandSender sender, String[] args) {
        if (args.length < 2) {
            help(sender);
            return;
        }
        String action = args[1].toLowerCase();
        String worldName = switch (action) {
            case "set" -> worldName(sender, args, 3);
            case "center" -> worldName(sender, args, 4);
            default -> worldName(sender, args, 2);
        };
        if (Bukkit.getWorld(worldName) == null) {
            sender.sendMessage("Welt ist nicht geladen: " + worldName);
            return;
        }
        switch (action) {
            case "set" -> {
                if (args.length < 3) throw new IllegalArgumentException("/chunkyvoid border set <Radius> [Welt]");
                int radius = Integer.parseInt(args[2]);
                int setback = plugin.getConfig().getInt("worlds." + worldName + ".border.setback", 10);
                if (radius <= setback || radius > 30_000_000) {
                    throw new IllegalArgumentException("Radius muss größer als der Rücksprung (" + setback + ") und höchstens 30000000 sein.");
                }
                plugin.setBorder(worldName, radius);
                sender.sendMessage("Unsichtbare Border in " + worldName + ": Radius " + radius
                        + " (Block " + (radius + 1L) + " außerhalb des Zentrums wird abgefangen).");
            }
            case "center" -> {
                if (args.length < 4) throw new IllegalArgumentException("/chunkyvoid border center <X> <Z> [Welt]");
                plugin.setCenter(worldName, Integer.parseInt(args[2]), Integer.parseInt(args[3]));
                sender.sendMessage("Border-Mittelpunkt für " + worldName + " gespeichert.");
            }
            case "off" -> {
                plugin.disableBorder(worldName);
                sender.sendMessage("Border in " + worldName + " deaktiviert.");
            }
            case "info" -> {
                BorderBounds bounds = plugin.border(worldName);
                sender.sendMessage(bounds == null ? "Keine aktive Border in " + worldName :
                        worldName + ": Mittelpunkt " + bounds.centerX() + "," + bounds.centerZ()
                                + ", Radius " + bounds.radius() + ", Rücksprung " + bounds.setback());
            }
            default -> help(sender);
        }
    }

    private void pregen(CommandSender sender, String[] args) {
        if (args.length < 2) {
            help(sender);
            return;
        }
        switch (args[1].toLowerCase()) {
            case "start" -> {
                String worldName = worldName(sender, args, 2);
                World world = Bukkit.getWorld(worldName);
                if (world == null) {
                    sender.sendMessage("Welt ist nicht geladen: " + worldName);
                    return;
                }
                BorderBounds bounds = plugin.border(worldName);
                if (bounds == null) {
                    sender.sendMessage("Zuerst /chunkyvoid border set <Radius> [Welt] ausführen.");
                    return;
                }
                sender.sendMessage(pregeneration.start(world, bounds));
            }
            case "pause" -> sender.sendMessage(pregeneration.pause());
            case "resume" -> sender.sendMessage(pregeneration.resume());
            case "cancel" -> sender.sendMessage(pregeneration.cancel());
            case "status" -> sender.sendMessage(pregeneration.status());
            default -> help(sender);
        }
    }

    private String worldName(CommandSender sender, String[] args, int index) {
        if (args.length > index) return args[index];
        if (sender instanceof Player player) return player.getWorld().getName();
        throw new IllegalArgumentException("In der Konsole bitte den Weltnamen angeben.");
    }

    private boolean help(CommandSender sender) {
        sender.sendMessage("/chunkyvoid border set <Radius> [Welt] | center <X> <Z> [Welt] | off [Welt] | info [Welt]");
        sender.sendMessage("/chunkyvoid pregen start [Welt] | pause | resume | cancel | status");
        sender.sendMessage("/chunkyvoid reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("chunkyvoid.admin")) return List.of();
        List<String> options = new ArrayList<>();
        if (args.length == 1) options.addAll(List.of("border", "pregen", "reload"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("border"))
            options.addAll(List.of("set", "center", "off", "info"));
        else if (args.length == 2 && args[0].equalsIgnoreCase("pregen"))
            options.addAll(List.of("start", "pause", "resume", "cancel", "status"));
        else if (args[0].equalsIgnoreCase("border") || args[0].equalsIgnoreCase("pregen"))
            Bukkit.getWorlds().forEach(world -> options.add(world.getName()));
        return options.stream().filter(option -> option.startsWith(args[args.length - 1])).toList();
    }
}
