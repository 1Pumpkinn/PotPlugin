package net.rose.potPlugin.util;

import java.util.LinkedHashMap;
import java.util.Map;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;

public final class PotionUtils {

    private PotionUtils() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static boolean isPotionMaterial(Material material) {
        return switch (material) {
            case POTION, SPLASH_POTION, LINGERING_POTION -> true;
            default -> false;
        };
    }

    public static boolean isExtendablePotion(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !isPotionMaterial(item.getType())) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof PotionMeta potionMeta)) {
            return false;
        }

        PotionType baseType = potionMeta.getBasePotionType();
        return baseType != null && BrewingConstants.EXTENDABLE_TYPES.contains(baseType);
    }

    /**
     * A plain, freshly-brewed potion of the given base type — used as the
     * exact-match input for the custom brewing recipe.
     */
    public static ItemStack basePotionItem(Material container, PotionType potionType) {
        ItemStack item = new ItemStack(container);
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof PotionMeta potionMeta)) {
            throw new IllegalStateException("Expected PotionMeta for material: " + container);
        }

        potionMeta.setBasePotionType(potionType);
        item.setItemMeta(potionMeta);
        return item;
    }

    /**
     * The 8-minute result item registered as the recipe's output.
     */
    public static ItemStack extendedPotionItem(Material container, PotionType potionType) {
        return buildExtendedPotion(basePotionItem(container, potionType));
    }

    /**
     * Rebuilds an existing potion item so its effect(s) last exactly the
     * extended duration. Used both for the recipe output and by the BrewEvent
     * safety net, so it must be safe to run on an already-extended potion.
     */
    public static ItemStack buildExtendedPotion(ItemStack original) {
        if (original == null) {
            return null;
        }

        ItemStack extended = original.clone();
        ItemMeta meta = extended.getItemMeta();
        if (!(meta instanceof PotionMeta potionMeta)) {
            return null;
        }

        // Grab this before applyExtendedDuration resets the base type to WATER.
        PotionType originalBase = potionMeta.getBasePotionType();

        applyExtendedDuration(potionMeta);

        // With the base type reset to WATER the client would call this a
        // "Water Bottle", so name it after the original effect. If it was
        // already extended (base is WATER), keep the name it already has.
        if (originalBase != null && originalBase != PotionType.WATER) {
            potionMeta.displayName(
                    Component.translatable(nameKeyPrefix(extended.getType()) + originalBase.getKey().getKey())
                            .decoration(TextDecoration.ITALIC, false)
            );
        }

        extended.setItemMeta(potionMeta);
        return extended;
    }

    private static String nameKeyPrefix(Material container) {
        return switch (container) {
            case SPLASH_POTION -> "item.minecraft.splash_potion.effect.";
            case LINGERING_POTION -> "item.minecraft.lingering_potion.effect.";
            default -> "item.minecraft.potion.effect.";
        };
    }

    /**
     * Collects every effect the potion currently has (base type + custom),
     * de-duplicated by effect type, then rewrites them all as custom effects
     * with the extended duration and resets the base type to WATER so the
     * original short effect can't show up next to the 8:00 one.
     */
    private static void applyExtendedDuration(PotionMeta meta) {
        Map<PotionEffectType, PotionEffect> merged = new LinkedHashMap<>();

        PotionType baseType = meta.getBasePotionType();
        if (baseType != null) {
            for (PotionEffect effect : baseType.getPotionEffects()) {
                merged.put(effect.getType(), effect);
            }
        }

        // Custom effects win over base ones of the same type.
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
            meta.addCustomEffect(effect.withDuration(BrewingConstants.EXTENDED_DURATION_TICKS), true);
        }
    }
}