package net.rose.potPlugin.enchantlimits;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.enchantments.EnchantmentOffer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Enforces the enchantment caps:
 * items a player holds get stripped to the allowed level (or have the
 * enchantment removed), and restricted enchantments can't be obtained from
 * the enchanting table or an anvil.
 */
public final class EnchantLimitsListener implements Listener {

    public static final String BYPASS_PERMISSION = "potplugin.enchantlimits.bypass";

    private final JavaPlugin plugin;
    private final EnchantLimits limits;

    public EnchantLimitsListener(JavaPlugin plugin, EnchantLimits limits) {
        this.plugin = plugin;
        this.limits = limits;
    }

    /** Strips inventories once a second (catches loot, trades, pickups, commands, anything else). */
    public void startSweepTask() {
        plugin.getServer().getScheduler().runTaskTimer(plugin, this::sweepAll, 20L, 20L);
    }

    public void sweepAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            sweep(player);
        }
    }

    private void sweep(Player player) {
        if (limits.isEmpty() || canBypass(player)) {
            return;
        }

        PlayerInventory inventory = player.getInventory();
        ItemStack[] contents = inventory.getContents();
        boolean changed = false;

        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (limits.violates(item)) {
                // Work on a copy and write it back, so the change always lands in the inventory.
                ItemStack fixed = item.clone();
                limits.sanitize(fixed);
                inventory.setItem(slot, fixed);
                changed = true;
            }
        }

        ItemStack cursor = player.getItemOnCursor();
        if (limits.violates(cursor)) {
            ItemStack fixed = cursor.clone();
            limits.sanitize(fixed);
            player.setItemOnCursor(fixed);
            changed = true;
        }

        if (changed) {
            alert(player, "Restricted enchantments were removed from your items.");
        }
    }

    // ------------------------------------------------------ enchanting table

    /** Hides / lowers offers so the table doesn't advertise restricted enchantments. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPrepareEnchant(PrepareItemEnchantEvent event) {
        if (limits.isEmpty() || canBypass(event.getEnchanter())) {
            return;
        }

        EnchantmentOffer[] offers = event.getOffers();
        for (int i = 0; i < offers.length; i++) {
            EnchantmentOffer offer = offers[i];
            if (offer == null || !limits.exceeds(offer.getEnchantment(), offer.getEnchantmentLevel())) {
                continue;
            }

            int max = limits.maxFor(offer.getEnchantment());
            if (max > 0) {
                offer.setEnchantmentLevel(max);
            } else {
                offers[i] = null;
            }
        }
    }

    /** The real guard: whatever the table was about to add gets stripped to the limits. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        Player player = event.getEnchanter();
        if (limits.isEmpty() || canBypass(player)) {
            return;
        }

        if (limits.sanitize(event.getEnchantsToAdd())) {
            if (event.getEnchantsToAdd().isEmpty()) {
                event.setCancelled(true);
                alert(player, "That enchantment is restricted on this server.");
            } else {
                alert(player, "Some enchantments were lowered or removed because they are restricted.");
            }
        }
    }

    // ----------------------------------------------------------------- anvil

    /** An anvil result carrying a restricted enchantment (too high a level, or not allowed) is blocked. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrepareAnvil(PrepareAnvilEvent event) {
        if (limits.isEmpty() || !limits.violates(event.getResult())) {
            return;
        }
        if (event.getView().getPlayer() instanceof Player player && canBypass(player)) {
            return;
        }

        event.setResult(null);
        if (event.getView().getPlayer() instanceof Player player) {
            alert(player, "That combination would create a restricted enchantment.");
        }
    }

    // ---------------------------------------------------------------- utils

    private static boolean canBypass(Player player) {
        return player.hasPermission(BYPASS_PERMISSION);
    }

    private static void alert(Player player, String message) {
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }
}
