package net.rose.potPlugin.recipe;

import net.rose.potPlugin.util.BrewingConstants;
import net.rose.potPlugin.util.PotionUtils;
import io.papermc.paper.potion.PotionMix;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionBrewer;
import org.bukkit.potion.PotionType;

public final class BrewingRecipeRegistrar {

    private final JavaPlugin plugin;
    private final List<NamespacedKey> registeredKeys = new ArrayList<>();

    public BrewingRecipeRegistrar(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerAll() {
        PotionBrewer brewer = plugin.getServer().getPotionBrewer();

        for (PotionType potionType : BrewingConstants.EXTENDABLE_TYPES) {
            for (Material container : BrewingConstants.POTION_CONTAINERS) {
                registerMix(brewer, potionType, container);
            }
        }

        plugin.getLogger().info("Registered " + registeredKeys.size() + " custom brewing recipe(s).");
    }

    public void unregisterAll() {
        PotionBrewer brewer = plugin.getServer().getPotionBrewer();
        for (NamespacedKey key : registeredKeys) {
            brewer.removePotionMix(key);
        }
        registeredKeys.clear();
    }

    private void registerMix(PotionBrewer brewer, PotionType potionType, Material container) {
        ItemStack input = PotionUtils.basePotionItem(container, potionType);
        ItemStack result = PotionUtils.extendedPotionItem(container, potionType);
        NamespacedKey key = new NamespacedKey(
                plugin,
                potionType.getKey().getKey() + "_" + container.name().toLowerCase()
        );

        // Defensive: a plugin /reload calls onEnable again without always
        // guaranteeing onDisable ran first, which previously threw on the
        // duplicate key. Clearing any prior mix with this key first is a
        // no-op if nothing was registered.
        brewer.removePotionMix(key);
        brewer.addPotionMix(new PotionMix(
                key,
                result,
                new RecipeChoice.ExactChoice(input),
                new RecipeChoice.MaterialChoice(BrewingConstants.EXTENSION_INGREDIENT)
        ));

        registeredKeys.add(key);
    }
}