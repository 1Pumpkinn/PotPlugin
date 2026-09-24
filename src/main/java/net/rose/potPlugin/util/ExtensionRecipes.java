package net.rose.potPlugin.util;

import io.papermc.paper.potion.PotionMix;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionType;

/**
 * Registers one brewing recipe per ladder step, per container:
 *   step 0 (vanilla potion) + redstone -> step 1
 *   step 1 + redstone                  -> step 2 ... up to the 8:00 potion.
 * Call register(this) from onEnable (replace the old single-recipe registration)
 * and unregister() from onDisable.
 */
public final class ExtensionRecipes {

    private static final List<NamespacedKey> KEYS = new ArrayList<>();

    private ExtensionRecipes() {
        throw new UnsupportedOperationException("Utility class");
    }

    public static void register(Plugin plugin) {
        RecipeChoice redstone = new RecipeChoice.MaterialChoice(BrewingConstants.EXTENSION_INGREDIENT);

        for (Material container : BrewingConstants.POTION_CONTAINERS) {
            for (Map.Entry<PotionType, int[]> entry : BrewingConstants.EXTENSION_STEPS.entrySet()) {
                PotionType type = entry.getKey();
                for (int step = 0; step < entry.getValue().length; step++) {
                    ItemStack input = PotionUtils.potionAtStep(container, type, step);
                    ItemStack result = PotionUtils.potionAtStep(container, type, step + 1);

                    NamespacedKey key = new NamespacedKey(plugin,
                            "extend_" + container.name().toLowerCase(Locale.ROOT)
                                    + "_" + type.getKey().getKey() + "_" + step);

                    Bukkit.getPotionBrewer().addPotionMix(
                            new PotionMix(key, result, new RecipeChoice.ExactChoice(input), redstone));
                    KEYS.add(key);
                }
            }
        }

        registerStrengthenMixes(plugin);
        registerContainerMixes(plugin);
        plugin.getLogger().info("Registered " + KEYS.size() + " custom brewing mixes.");
    }

    /** Vanilla long potion (e.g. Strength 8:00) + glowstone -> level II potion at the top of its ladder. */
    private static void registerStrengthenMixes(Plugin plugin) {
        RecipeChoice glowstone = new RecipeChoice.MaterialChoice(BrewingConstants.STRENGTHEN_INGREDIENT);

        for (Material container : BrewingConstants.POTION_CONTAINERS) {
            for (Map.Entry<PotionType, PotionType> entry : BrewingConstants.LONG_TO_STRONG.entrySet()) {
                PotionType longType = entry.getKey();
                PotionType strongType = entry.getValue();

                int[] ladder = BrewingConstants.EXTENSION_STEPS.get(strongType);
                if (ladder == null) {
                    continue;
                }

                ItemStack input = PotionUtils.basePotionItem(container, longType);
                ItemStack result = PotionUtils.potionAtStep(container, strongType, ladder.length);

                NamespacedKey key = new NamespacedKey(plugin,
                        "strengthen_" + container.name().toLowerCase(Locale.ROOT)
                                + "_" + longType.getKey().getKey());

                Bukkit.getPotionBrewer().addPotionMix(
                        new PotionMix(key, result, new RecipeChoice.ExactChoice(input), glowstone));
                KEYS.add(key);
            }
        }
    }

    /**
     * Vanilla's gunpowder / dragon's breath conversion rebuilds the item from the
     * potion's BASE type only, which throws away our custom effects and turns
     * extended potions into plain water. So every extended (step 1+) potion gets
     * its own conversion recipe:
     *   potion + gunpowder -> splash, splash + dragon's breath -> lingering.
     */
    private static void registerContainerMixes(Plugin plugin) {
        RecipeChoice gunpowder = new RecipeChoice.MaterialChoice(Material.GUNPOWDER);
        RecipeChoice dragonBreath = new RecipeChoice.MaterialChoice(Material.DRAGON_BREATH);

        for (Map.Entry<PotionType, int[]> entry : BrewingConstants.EXTENSION_STEPS.entrySet()) {
            PotionType type = entry.getKey();
            for (int step = 1; step <= entry.getValue().length; step++) {
                registerConversion(plugin, type, step, Material.POTION, Material.SPLASH_POTION, gunpowder);
                registerConversion(plugin, type, step, Material.SPLASH_POTION, Material.LINGERING_POTION, dragonBreath);
            }
        }
    }

    private static void registerConversion(Plugin plugin, PotionType type, int step,
                                           Material from, Material to, RecipeChoice ingredient) {
        ItemStack input = PotionUtils.potionAtStep(from, type, step);
        ItemStack result = PotionUtils.potionAtStep(to, type, step);

        NamespacedKey key = new NamespacedKey(plugin,
                "convert_" + from.name().toLowerCase(Locale.ROOT)
                        + "_to_" + to.name().toLowerCase(Locale.ROOT)
                        + "_" + type.getKey().getKey() + "_" + step);

        Bukkit.getPotionBrewer().addPotionMix(
                new PotionMix(key, result, new RecipeChoice.ExactChoice(input), ingredient));
        KEYS.add(key);
    }

    public static void unregister() {
        for (NamespacedKey key : KEYS) {
            Bukkit.getPotionBrewer().removePotionMix(key);
        }
        KEYS.clear();
    }
}