package net.rose.potPlugin.enchantlimits;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.EnchantmentStorageMeta;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Per-enchantment level caps, stored in config.yml as "name=maxLevel"
 * (e.g. {@code sharpness=3}). A cap of 0 means the enchantment isn't allowed
 * at all. Also holds the strip / check logic everything else uses.
 */
public final class EnchantLimits {

    private static final String CONFIG_PATH = "enchant-limits";

    private final JavaPlugin plugin;
    private final Map<String, Integer> limits = new TreeMap<>();

    public EnchantLimits(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ---------------------------------------------------------------- storage

    public void load() {
        plugin.reloadConfig();
        limits.clear();
        for (String raw : plugin.getConfig().getStringList(CONFIG_PATH)) {
            String[] parts = raw.split("=", 2);
            String name = parts.length == 2 ? normalize(parts[0]) : null;
            Integer level = parts.length == 2 ? parseLevel(parts[1]) : null;
            if (name != null && level != null && lookup(name) != null) {
                limits.put(name, level);
            } else {
                plugin.getLogger().warning("Ignoring invalid enchant-limits entry in config.yml: " + raw);
            }
        }
    }

    public void save() {
        List<String> entries = new ArrayList<>();
        limits.forEach((name, level) -> entries.add(name + "=" + level));
        plugin.getConfig().set(CONFIG_PATH, entries);
        plugin.saveConfig();
    }

    // ---------------------------------------------------------------- editing

    public void set(String name, int maxLevel) {
        limits.put(name, maxLevel);
    }

    /** @return true if a limit existed */
    public boolean remove(String name) {
        return limits.remove(name) != null;
    }

    public Map<String, Integer> all() {
        return Map.copyOf(limits);
    }

    public boolean isEmpty() {
        return limits.isEmpty();
    }

    /** The key to store/type for an enchantment, or null if it doesn't exist. "sharpness", or "ns:name" for non-vanilla. */
    public String resolve(String input) {
        String name = normalize(input);
        return lookup(name) == null ? null : name;
    }

    public Enchantment enchantment(String name) {
        return lookup(name);
    }

    public List<String> allEnchantNames() {
        List<String> names = new ArrayList<>();
        for (Enchantment enchantment : Registry.ENCHANTMENT) {
            names.add(nameOf(enchantment));
        }
        return names;
    }

    // ----------------------------------------------------------------- checks

    /** The cap for this enchantment (0 = not allowed), or null if it has no limit. */
    public Integer maxFor(Enchantment enchantment) {
        return limits.get(nameOf(enchantment));
    }

    /** True if this level is above the cap (or the enchantment isn't allowed). */
    public boolean exceeds(Enchantment enchantment, int level) {
        Integer max = maxFor(enchantment);
        return max != null && level > max;
    }

    /** True if the item carries any enchantment (applied or stored in a book) over its cap. */
    public boolean violates(ItemStack item) {
        if (limits.isEmpty() || item == null || item.getType().isAir()) {
            return false;
        }
        for (Map.Entry<Enchantment, Integer> entry : item.getEnchantments().entrySet()) {
            if (exceeds(entry.getKey(), entry.getValue())) {
                return true;
            }
        }
        if (item.hasItemMeta() && item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            for (Map.Entry<Enchantment, Integer> entry : meta.getStoredEnchants().entrySet()) {
                if (exceeds(entry.getKey(), entry.getValue())) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Brings the item within the limits: over-cap enchantments are lowered to
     * the cap, disallowed ones are removed. Works on enchanted books too.
     * Edits the given stack in place.
     *
     * @return true if anything changed
     */
    public boolean sanitize(ItemStack item) {
        if (!violates(item)) {
            return false;
        }

        for (Map.Entry<Enchantment, Integer> entry : new ArrayList<>(item.getEnchantments().entrySet())) {
            Enchantment enchantment = entry.getKey();
            if (exceeds(enchantment, entry.getValue())) {
                item.removeEnchantment(enchantment);
                int max = maxFor(enchantment);
                if (max > 0) {
                    item.addUnsafeEnchantment(enchantment, max);
                }
            }
        }

        if (item.getItemMeta() instanceof EnchantmentStorageMeta meta) {
            boolean metaChanged = false;
            for (Map.Entry<Enchantment, Integer> entry : new ArrayList<>(meta.getStoredEnchants().entrySet())) {
                Enchantment enchantment = entry.getKey();
                if (exceeds(enchantment, entry.getValue())) {
                    meta.removeStoredEnchant(enchantment);
                    int max = maxFor(enchantment);
                    if (max > 0) {
                        meta.addStoredEnchant(enchantment, max, true);
                    }
                    metaChanged = true;
                }
            }
            if (metaChanged) {
                item.setItemMeta(meta);
            }
        }
        return true;
    }

    /**
     * Same as {@link #sanitize(ItemStack)} but for a plain enchantment map
     * (the enchantment table's pending result).
     *
     * @return true if anything changed
     */
    public boolean sanitize(Map<Enchantment, Integer> enchantments) {
        boolean changed = false;
        for (Map.Entry<Enchantment, Integer> entry : new ArrayList<>(enchantments.entrySet())) {
            Enchantment enchantment = entry.getKey();
            if (exceeds(enchantment, entry.getValue())) {
                int max = maxFor(enchantment);
                if (max > 0) {
                    enchantments.put(enchantment, max);
                } else {
                    enchantments.remove(enchantment);
                }
                changed = true;
            }
        }
        return changed;
    }

    // ---------------------------------------------------------------- helpers

    private static String nameOf(Enchantment enchantment) {
        NamespacedKey key = enchantment.getKey();
        return key.getNamespace().equals(NamespacedKey.MINECRAFT) ? key.getKey() : key.toString();
    }

    private static Enchantment lookup(String name) {
        NamespacedKey key = NamespacedKey.fromString(name.contains(":") ? name : "minecraft:" + name);
        return key == null ? null : Registry.ENCHANTMENT.get(key);
    }

    private static String normalize(String raw) {
        String name = raw.trim().toLowerCase(Locale.ROOT);
        return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
    }

    private static Integer parseLevel(String raw) {
        try {
            int level = Integer.parseInt(raw.trim());
            return level >= 0 ? level : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
