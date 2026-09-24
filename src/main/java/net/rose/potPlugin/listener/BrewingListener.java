package net.rose.potPlugin.listener;

import net.rose.potPlugin.util.BrewingConstants;
import net.rose.potPlugin.util.PotionUtils;
import net.rose.potPlugin.recipe.BrewingRecipeRegistrar;
import java.util.List;
import java.util.logging.Logger;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;

/**
 * Safety net for the custom PotionMix recipes in
 * {@link net.rose.potPlugin.recipe.BrewingRecipeRegistrar}.
 *
 * <p>PotionMix already produces the correct 8-minute item on its own, but
 * it only matches an exact pre-built input item. This listener recomputes
 * the extension directly from whatever is actually sitting in the bottle
 * slot when Redstone is the ingredient, and overwrites the pending brew
 * result by mutating the list from {@link BrewEvent#getResults()} in
 * place — that list is the live, mutable backing for the brew outcome
 * (there is no separate setter). Writing to the inventory directly
 * instead would get silently discarded, since Bukkit applies its own
 * computed results to the inventory right after this event returns.</p>
 */
public final class BrewingListener implements Listener {

    private static final int[] BOTTLE_SLOTS = {0, 1, 2};

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        BrewerInventory contents = event.getContents();
        ItemStack ingredient = contents.getIngredient();
        if (ingredient == null || ingredient.getType() != BrewingConstants.EXTENSION_INGREDIENT) {
            return;
        }

        List<ItemStack> results = event.getResults();

        for (int slot : BOTTLE_SLOTS) {
            ItemStack bottle = contents.getItem(slot);
            if (!PotionUtils.isExtendablePotion(bottle)) {
                continue;
            }

            ItemStack extended = PotionUtils.buildExtendedPotion(bottle);
            if (extended == null) {
                logWarning(slot, bottle);
                continue;
            }

            results.set(slot, extended);
        }
    }

    private static void logWarning(int slot, ItemStack bottle) {
        Logger.getLogger("Potions01").warning(
                "[Potions01] Could not build extended potion for slot " + slot
                        + " (type=" + bottle.getType() + "). Skipping.");
    }
}