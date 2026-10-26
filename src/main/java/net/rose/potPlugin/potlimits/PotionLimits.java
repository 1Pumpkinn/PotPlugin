package net.rose.potPlugin.potlimits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.rose.potPlugin.util.PotionUtils;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionType;

/**
 * The list of restricted potion types (stored in config.yml as the type's key,
 * e.g. {@code strong_strength}) plus every "is this restricted?" check the
 * rest of the feature uses.
 */
public final class PotionLimits {

    private static final String CONFIG_PATH = "banned-potions";

    private static final Set<Material> POTION_ITEMS = Set.of(
            Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION, Material.TIPPED_ARROW);

    private final JavaPlugin plugin;
    private final Set<String> banned = new TreeSet<>();

    /** Effects (type + level) that mobs / arrows / clouds are not allowed to carry. */
    private List<PotionEffect> blockedEffects = List.of();

    public PotionLimits(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- storage

    public void load() {
        plugin.reloadConfig();
        banned.clear();
        for (String raw : plugin.getConfig().getStringList(CONFIG_PATH)) {
            String name = normalize(raw);
            if (lookup(name) != null) {
                banned.add(name);
            } else {
                plugin.getLogger().warning("Ignoring unknown potion type in config.yml: " + raw);
            }
        }
        rebuildEffects();
    }

    public void save() {
        plugin.getConfig().set(CONFIG_PATH, new ArrayList<>(banned));
        plugin.saveConfig();
    }

    // ---------------------------------------------------------------- editing

    /** @return true if it was newly added */
    public boolean ban(String name) {
        boolean added = banned.add(name);
        rebuildEffects();
        return added;
    }

    /** @return true if it was actually on the list */
    public boolean unban(String name) {
        boolean removed = banned.remove(name);
        rebuildEffects();
        return removed;
    }

    public Set<String> banned() {
        return Set.copyOf(banned);
    }

    public boolean isEmpty() {
        return banned.isEmpty();
    }

    /**
     * Turns user input into valid type keys. "strong_strength" gives just that
     * type; "strength*" gives the whole family (strength, long_strength,
     * strong_strength) for the ones that exist. Empty list = unknown potion.
     */
    public List<String> expand(String input) {
        String name = normalize(input);
        List<String> candidates;
        if (name.endsWith("*")) {
            String base = stripVariant(name.substring(0, name.length() - 1));
            candidates = List.of(base, "long_" + base, "strong_" + base);
        } else {
            candidates = List.of(name);
        }

        List<String> result = new ArrayList<>();
        for (String candidate : candidates) {
            if (lookup(candidate) != null) {
                result.add(candidate);
            }
        }
        return result;
    }

    /** Every potion type key that exists in this server version (for tab completion). */
    public List<String> allTypeNames() {
        List<String> names = new ArrayList<>();
        for (PotionType type : Registry.POTION) {
            names.add(type.getKey().getKey());
        }
        return names;
    }

    // ----------------------------------------------------------------- checks

    public boolean isBanned(PotionType type) {
        return type != null && banned.contains(type.getKey().getKey());
    }

    /** Potion / splash / lingering / tipped arrow whose base type or original (pre-extension) type is restricted. */
    public boolean isBanned(ItemStack item) {
        if (banned.isEmpty() || item == null || !POTION_ITEMS.contains(item.getType())) {
            return false;
        }
        if (!(item.getItemMeta() instanceof PotionMeta meta)) {
            return false;
        }
        return isBanned(meta.getBasePotionType()) || isBanned(PotionUtils.originOf(meta));
    }

    /**
     * True if this effect is what a restricted type would apply. The level
     * counts: banning strong_strength blocks Strength II+ but not Strength I.
     * Long variants only differ in duration, so they're blocked as items and
     * arrows but not by effect (that would also block the normal potion).
     */
    public boolean blocksEffect(PotionEffect effect) {
        for (PotionEffect blocked : blockedEffects) {
            if (blocked.getType().equals(effect.getType()) && effect.getAmplifier() >= blocked.getAmplifier()) {
                return true;
            }
        }
        return false;
    }

    // ---------------------------------------------------------------- helpers

    private void rebuildEffects() {
        List<PotionEffect> effects = new ArrayList<>();
        for (String name : banned) {
            if (name.startsWith("long_")) {
                continue;
            }
            PotionType type = lookup(name);
            if (type != null) {
                effects.addAll(type.getPotionEffects());
            }
        }
        blockedEffects = List.copyOf(effects);
    }

    private static PotionType lookup(String name) {
        NamespacedKey key = NamespacedKey.fromString("minecraft:" + name);
        return key == null ? null : Registry.POTION.get(key);
    }

    private static String normalize(String raw) {
        String name = raw.trim().toLowerCase(Locale.ROOT);
        return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
    }

    private static String stripVariant(String name) {
        if (name.startsWith("long_")) {
            return name.substring("long_".length());
        }
        if (name.startsWith("strong_")) {
            return name.substring("strong_".length());
        }
        return name;
    }
}
