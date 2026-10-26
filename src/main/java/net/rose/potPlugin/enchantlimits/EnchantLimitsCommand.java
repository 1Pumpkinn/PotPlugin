package net.rose.potPlugin.enchantlimits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.enchantments.Enchantment;

/**
 * /enchantlimits set <enchant> <maxLevel>   cap an enchantment (0 = not allowed)
 * /enchantlimits ban <enchant>              same as set <enchant> 0
 * /enchantlimits remove <enchant>           lift the limit
 * /enchantlimits list | reload
 */
public final class EnchantLimitsCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("set", "ban", "remove", "list", "reload");

    private final EnchantLimits limits;
    private final EnchantLimitsListener listener;

    public EnchantLimitsCommand(EnchantLimits limits, EnchantLimitsListener listener) {
        this.limits = limits;
        this.listener = listener;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            usage(sender, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "list" -> list(sender);
            case "reload" -> {
                limits.load();
                listener.sweepAll();
                send(sender, "Reloaded " + limits.all().size() + " enchantment limit(s).", NamedTextColor.GREEN);
            }
            case "set" -> set(sender, label, args);
            case "ban" -> ban(sender, label, args);
            case "remove" -> remove(sender, label, args);
            default -> usage(sender, label);
        }
        return true;
    }

    private void set(CommandSender sender, String label, String[] args) {
        if (args.length < 3) {
            usage(sender, label);
            return;
        }

        String name = limits.resolve(args[1]);
        if (name == null) {
            send(sender, "Unknown enchantment: " + args[1], NamedTextColor.RED);
            return;
        }

        int level;
        try {
            level = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            level = -1;
        }
        if (level < 0) {
            send(sender, "Max level must be a number from 0 (not allowed) upwards.", NamedTextColor.RED);
            return;
        }

        apply(sender, name, level);
    }

    private void ban(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            usage(sender, label);
            return;
        }

        String name = limits.resolve(args[1]);
        if (name == null) {
            send(sender, "Unknown enchantment: " + args[1], NamedTextColor.RED);
            return;
        }
        apply(sender, name, 0);
    }

    private void apply(CommandSender sender, String name, int level) {
        limits.set(name, level);
        limits.save();
        listener.sweepAll();
        send(sender, level == 0
                ? name + " is no longer allowed."
                : name + " is now capped at level " + level + ".", NamedTextColor.GREEN);
    }

    private void remove(CommandSender sender, String label, String[] args) {
        if (args.length < 2) {
            usage(sender, label);
            return;
        }

        String name = limits.resolve(args[1]);
        if (name == null) {
            send(sender, "Unknown enchantment: " + args[1], NamedTextColor.RED);
            return;
        }

        if (!limits.remove(name)) {
            send(sender, name + " has no limit.", NamedTextColor.YELLOW);
            return;
        }
        limits.save();
        send(sender, name + " is unrestricted again.", NamedTextColor.GREEN);
    }

    private void list(CommandSender sender) {
        if (limits.isEmpty()) {
            send(sender, "No enchantments are limited.", NamedTextColor.YELLOW);
            return;
        }

        List<String> parts = new ArrayList<>();
        new TreeMap<>(limits.all()).forEach((name, level) ->
                parts.add(level == 0 ? name + " (banned)" : name + " (max " + level + ")"));
        send(sender, "Enchantment limits: " + String.join(", ", parts), NamedTextColor.GOLD);
    }

    private void usage(CommandSender sender, String label) {
        send(sender, "/" + label + " set <enchant> <maxLevel>  |  ban <enchant>  |  remove <enchant>  |  list  |  reload",
                NamedTextColor.GRAY);
    }

    private static void send(CommandSender sender, String message, NamedTextColor color) {
        sender.sendMessage(Component.text(message, color));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(SUBCOMMANDS, args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (args.length == 2) {
            return switch (sub) {
                case "set", "ban" -> filter(limits.allEnchantNames(), args[1]);
                case "remove" -> filter(new ArrayList<>(limits.all().keySet()), args[1]);
                default -> List.of();
            };
        }

        if (args.length == 3 && sub.equals("set")) {
            String name = limits.resolve(args[1]);
            Enchantment enchantment = name == null ? null : limits.enchantment(name);
            if (enchantment != null) {
                List<String> levels = new ArrayList<>();
                for (int level = 0; level <= enchantment.getMaxLevel(); level++) {
                    levels.add(String.valueOf(level));
                }
                return filter(levels, args[2]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(lower)).sorted().toList();
    }
}
