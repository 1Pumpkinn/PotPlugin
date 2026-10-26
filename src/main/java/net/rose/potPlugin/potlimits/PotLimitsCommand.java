package net.rose.potPlugin.potlimits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;

/**
 * /potlimits add|remove|list|reload
 *   add strong_strength   restrict one exact type
 *   add strength*         restrict the whole family (strength, long_strength, strong_strength)
 */
public final class PotLimitsCommand implements TabExecutor {

    private static final List<String> SUBCOMMANDS = List.of("add", "remove", "list", "reload");

    private final PotionLimits limits;
    private final PotLimitsListener listener;

    public PotLimitsCommand(PotionLimits limits, PotLimitsListener listener) {
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
                send(sender, "Reloaded " + limits.banned().size() + " restricted potion type(s).", NamedTextColor.GREEN);
            }
            case "add" -> change(sender, label, args, true);
            case "remove" -> change(sender, label, args, false);
            default -> usage(sender, label);
        }
        return true;
    }

    private void change(CommandSender sender, String label, String[] args, boolean add) {
        if (args.length < 2) {
            usage(sender, label);
            return;
        }

        List<String> names = limits.expand(args[1]);
        if (names.isEmpty()) {
            send(sender, "Unknown potion type: " + args[1], NamedTextColor.RED);
            return;
        }

        List<String> changed = new ArrayList<>();
        for (String name : names) {
            if (add ? limits.ban(name) : limits.unban(name)) {
                changed.add(name);
            }
        }

        if (changed.isEmpty()) {
            send(sender, add ? "Already restricted." : "That wasn't restricted.", NamedTextColor.YELLOW);
            return;
        }

        limits.save();
        if (add) {
            listener.sweepAll();
        }
        send(sender, (add ? "Restricted: " : "Allowed again: ") + String.join(", ", changed), NamedTextColor.GREEN);
    }

    private void list(CommandSender sender) {
        if (limits.isEmpty()) {
            send(sender, "No potions are restricted.", NamedTextColor.YELLOW);
            return;
        }
        send(sender, "Restricted potions (" + limits.banned().size() + "): "
                + String.join(", ", new java.util.TreeSet<>(limits.banned())), NamedTextColor.GOLD);
    }

    private void usage(CommandSender sender, String label) {
        send(sender, "/" + label + " <add|remove> <potion|potion*>  |  /" + label + " list  |  /" + label + " reload",
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
        if (args.length == 2) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("add")) {
                List<String> options = new ArrayList<>(limits.allTypeNames());
                options.removeAll(limits.banned());
                return filter(options, args[1]);
            }
            if (sub.equals("remove")) {
                return filter(new ArrayList<>(limits.banned()), args[1]);
            }
        }
        return List.of();
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return options.stream().filter(option -> option.startsWith(lower)).sorted().toList();
    }
}
