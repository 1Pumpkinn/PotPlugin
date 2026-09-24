package net.rose.potPlugin.util;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public final class PotionUtils {

    /** How many redstone steps this potion has already had (missing = 0). */
    private static final NamespacedKey STEP_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("potplugin:extension_step"));
    /** The vanilla potion type it started as (needed because the base type is reset to WATER). */
    private static final NamespacedKey ORIGIN_KEY =
            Objects.requireNonNull(NamespacedKey.fromString("potplugin:extension_origin"));

    private PotionUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isPotionMaterial(Material material) {
        return switch (material) {
            case POTION, SPLASH_POTION, LINGERING_POTION -> true;
            default -> false;
        };
    }

    /** True if one more redstone would move this potion up its ladder. */
    public static boolean isExtendablePotion(ItemStack item) {
        PotionMeta meta = potionMeta(item);
        if (meta == null) {
            return false;
        }

        PotionType origin = originType(meta);
        int[] steps = origin == null ? null : BrewingConstants.EXTENSION_STEPS.get(origin);
        return steps != null && currentStep(meta) < steps.length;
    }

    /**
     * A plain, freshly-brewed vanilla potion of the given type. This is also
     * step 0 of every ladder and the exact-match input for the first recipe.
     */
    public static ItemStack basePotionItem(Material container, PotionType potionType) {
        ItemStack item = new ItemStack(container);
        if (!(item.getItemMeta() instanceof PotionMeta potionMeta)) {
            throw new IllegalStateException("Expected PotionMeta for material: " + container);
        }

        potionMeta.setBasePotionType(potionType);
        item.setItemMeta(potionMeta);
        return item;
    }

    /**
     * The potion as it looks after {@code step} redstone additions.
     * Step 0 is the untouched vanilla potion; the last step is the 8:00 one.
     */
    public static ItemStack potionAtStep(Material container, PotionType potionType, int step) {
        ItemStack item = basePotionItem(container, potionType);
        if (step <= 0 || !BrewingConstants.EXTENSION_STEPS.containsKey(potionType)) {
            return item;
        }

        writeStep(item, potionType, step);
        return item;
    }

    /** @deprecated use {@link #potionAtStep}; this is only the first step of the ladder. */
    @Deprecated
    public static ItemStack extendedPotionItem(Material container, PotionType potionType) {
        return potionAtStep(container, potionType, 1);
    }

    /**
     * Returns the given potion advanced by exactly one redstone step. Used by
     * the BrewEvent safety net. Potions that can't be extended (vanilla LONG_*
     * potions, other potions, already at 8:00) come back unchanged.
     */
    public static ItemStack buildExtendedPotion(ItemStack original) {
        PotionMeta meta = potionMeta(original);
        if (meta == null) {
            return null;
        }

        ItemStack result = original.clone();
        if (!isExtendablePotion(original)) {
            return result;
        }

        writeStep(result, originType(meta), currentStep(meta) + 1);
        return result;
    }

    // ---------------------------------------------------------------------

    private static PotionMeta potionMeta(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !isPotionMaterial(item.getType())) {
            return null;
        }
        return item.getItemMeta() instanceof PotionMeta meta ? meta : null;
    }

    private static int currentStep(PotionMeta meta) {
        return meta.getPersistentDataContainer().getOrDefault(STEP_KEY, PersistentDataType.INTEGER, 0);
    }

    private static PotionType originType(PotionMeta meta) {
        String stored = meta.getPersistentDataContainer().get(ORIGIN_KEY, PersistentDataType.STRING);
        if (stored != null) {
            NamespacedKey key = NamespacedKey.fromString(stored);
            PotionType type = key == null ? null : Registry.POTION.get(key);
            if (type != null) {
                return type;
            }
        }
        return meta.getBasePotionType();
    }

    private static void writeStep(ItemStack item, PotionType origin, int step) {
        int[] steps = BrewingConstants.EXTENSION_STEPS.get(origin);
        int index = Math.max(1, Math.min(step, steps.length));

        PotionMeta meta = (PotionMeta) item.getItemMeta();
        setEffectDuration(meta, steps[index - 1]);

        // Base type is WATER now, so name it after the original effect.
        meta.displayName(
                Component.translatable(nameKeyPrefix(item.getType()) + nameKey(origin))
                        .decoration(TextDecoration.ITALIC, false)
        );

        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        pdc.set(STEP_KEY, PersistentDataType.INTEGER, index);
        pdc.set(ORIGIN_KEY, PersistentDataType.STRING, origin.getKey().toString());
        item.setItemMeta(meta);
    }

    private static String nameKeyPrefix(Material container) {
        return switch (container) {
            case SPLASH_POTION -> "item.minecraft.splash_potion.effect.";
            case LINGERING_POTION -> "item.minecraft.lingering_potion.effect.";
            default -> "item.minecraft.potion.effect.";
        };
    }

    /** Vanilla only has lang keys for the base name: long_x / strong_x reuse "x". */
    private static String nameKey(PotionType type) {
        String key = type.getKey().getKey();
        if (key.startsWith("long_")) {
            return key.substring("long_".length());
        }
        if (key.startsWith("strong_")) {
            return key.substring("strong_".length());
        }
        return key;
    }

    /**
     * Collects every effect the potion currently has (base type + custom),
     * de-duplicated by effect type, then rewrites them all as custom effects
     * with the given duration and resets the base type to WATER so the
     * original short effect can't show up next to the new one.
     */
    private static void setEffectDuration(PotionMeta meta, int durationTicks) {
        Map<PotionEffectType, PotionEffect> merged = new LinkedHashMap<>();

        PotionType baseType = meta.getBasePotionType();
        if (baseType != null) {
            for (PotionEffect effect : baseType.getPotionEffects()) {
                merged.put(effect.getType(), effect);
            }
        }

        if (meta.hasCustomEffects()) {
            for (PotionEffect effect : meta.getCustomEffects()) {
                merged.put(effect.getType(), effect);
            }
        }

        if (merged.isEmpty()) {
            return;
        }

        meta.clearCustomEffects();
        meta.setBasePotionType(PotionType.WATER);
        for (PotionEffect effect : merged.values()) {
            meta.addCustomEffect(effect.withDuration(durationTicks), true);
        }
    }
}