package net.rose.potPlugin.util;

import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
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
     * Keeps the base potion type set (unlike the previous implementation)
     * so color/identity stay consistent with a normal potion of this type.
     */
    public static ItemStack extendedPotionItem(Material container, PotionType potionType) {
        ItemStack item = basePotionItem(container, potionType);
        ItemMeta meta = item.getItemMeta();
        if (!(meta instanceof PotionMeta potionMeta)) {
            throw new IllegalStateException("Expected PotionMeta for material: " + container);
        }

        applyExtendedDuration(potionMeta);
        potionMeta.displayName(
                Component.translatable("item.minecraft.potion.effect." + potionType.getKey().getKey())
                        .decoration(TextDecoration.ITALIC, false)
        );
        item.setItemMeta(potionMeta);
        return item;
    }

    /**
     * Rebuilds an already-existing potion item with the extended duration
     * applied. Used by the BrewEvent safety net to recompute the result
     * directly from whatever is actually in the bottle slot.
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

        applyExtendedDuration(potionMeta);
        extended.setItemMeta(potionMeta);
        return extended;
    }

    private static void applyExtendedDuration(PotionMeta meta) {
        if (meta.hasCustomEffects()) {
            List<PotionEffect> updated = meta.getCustomEffects().stream()
                    .map(effect -> effect.withDuration(BrewingConstants.EXTENDED_DURATION_TICKS))
                    .toList();
            meta.clearCustomEffects();
            updated.forEach(effect -> meta.addCustomEffect(effect, true));
            return;
        }

        PotionType baseType = meta.getBasePotionType();
        if (baseType == null) {
            return;
        }

        baseType.getPotionEffects().stream().findFirst().ifPresent(primary ->
                meta.addCustomEffect(primary.withDuration(BrewingConstants.EXTENDED_DURATION_TICKS), true));
    }
}