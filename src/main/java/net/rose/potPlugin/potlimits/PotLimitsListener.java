package net.rose.potPlugin.potlimits;

import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockDispenseEvent;
import org.bukkit.event.block.BlockDispenseLootEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.CreatureSpawnEvent.SpawnReason;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.BrewEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.world.LootGenerateEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;

/**
 * Enforces the restricted-potion list:
 * can't brew, can't pick up / hold / drink, and the potion never applies
 * through dispensers, trial chamber loot, trial spawner mobs or tipped arrows.
 */
public final class PotLimitsListener implements Listener {

    public static final String BYPASS_PERMISSION = "potplugin.potlimits.bypass";

    private final JavaPlugin plugin;
    private final PotionLimits limits;

    public PotLimitsListener(JavaPlugin plugin, PotionLimits limits) {
        this.plugin = plugin;
        this.limits = limits;
    }

    /** Removes restricted potions from inventories once a second (catches anything the events miss). */
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
        boolean removed = false;

        for (int slot = 0; slot < contents.length; slot++) {
            if (limits.isBanned(contents[slot])) {
                inventory.setItem(slot, null);
                removed = true;
            }
        }
        if (limits.isBanned(player.getItemOnCursor())) {
            player.setItemOnCursor(null);
            removed = true;
        }
        if (removed) {
            deny(player, "A restricted potion was removed from your inventory.");
        }
    }

    // ------------------------------------------------------------ can't brew

    /** HIGHEST so it runs after BrewingListener has rewritten the results. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBrew(BrewEvent event) {
        for (ItemStack result : event.getResults()) {
            if (limits.isBanned(result)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    // ------------------------------------------------- can't hold or drink

    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player player
                && !canBypass(player)
                && limits.isBanned(event.getItem().getItemStack())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player) || canBypass(player)) {
            return;
        }

        boolean restricted = limits.isBanned(event.getCurrentItem()) || limits.isBanned(event.getCursor());

        int hotbarButton = event.getHotbarButton();
        if (!restricted && hotbarButton >= 0) {
            restricted = limits.isBanned(player.getInventory().getItem(hotbarButton));
        }
        if (!restricted && event.getClick() == ClickType.SWAP_OFFHAND) {
            restricted = limits.isBanned(player.getInventory().getItemInOffHand());
        }

        if (restricted) {
            event.setCancelled(true);
            deny(player, "That potion is restricted on this server.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (event.getWhoClicked() instanceof Player player
                && !canBypass(player)
                && limits.isBanned(event.getOldCursor())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        if (!canBypass(event.getPlayer()) && limits.isBanned(event.getItem())) {
            event.setCancelled(true);
            deny(event.getPlayer(), "That potion is restricted on this server.");
        }
    }

    // ------------------------------------- never applies (dispensers, mobs)

    @EventHandler(ignoreCancelled = true)
    public void onDispense(BlockDispenseEvent event) {
        if (limits.isBanned(event.getItem())) {
            event.setCancelled(true);
        }
    }

    /** Any restricted splash/lingering potion thrown by anything (dispenser, witch, player). */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity() instanceof ThrownPotion potion) {
            if (limits.isBanned(potion.getItem())) {
                event.setCancelled(true);
            }
        } else if (event.getEntity() instanceof Arrow arrow) {
            stripArrow(arrow);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        if (limits.isBanned(event.getPotion().getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onCloud(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        boolean restricted = limits.isBanned(cloud.getBasePotionType());
        for (PotionEffect effect : cloud.getCustomEffects()) {
            restricted |= limits.blocksEffect(effect);
        }
        if (restricted) {
            event.setCancelled(true);
        }
    }

    /** Keeps the arrow itself but removes the restricted effect (e.g. a bogged's poison arrows). */
    private void stripArrow(Arrow arrow) {
        if (limits.isBanned(arrow.getBasePotionType())) {
            arrow.setBasePotionType(null);
        }
        for (PotionEffect effect : new ArrayList<>(arrow.getCustomEffects())) {
            if (limits.blocksEffect(effect)) {
                arrow.removeCustomEffect(effect.getType());
            }
        }
    }

    // ------------------------------------------------------- trial chambers

    /** Trial spawner mobs spawn with their effects already on; strip the restricted ones. */
    @EventHandler(ignoreCancelled = true)
    public void onTrialSpawn(CreatureSpawnEvent event) {
        if (event.getSpawnReason() != SpawnReason.TRIAL_SPAWNER) {
            return;
        }

        LivingEntity mob = event.getEntity();
        stripMobEffects(mob);
        // Some effects only land after the spawn event, so check once more next tick.
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (mob.isValid()) {
                stripMobEffects(mob);
            }
        });
    }

    private void stripMobEffects(LivingEntity mob) {
        for (PotionEffect effect : mob.getActivePotionEffects()) {
            if (limits.blocksEffect(effect)) {
                mob.removePotionEffect(effect.getType());
            }
        }
    }

    /** Trial vault / ominous vault / trial spawner rewards. */
    @EventHandler
    public void onVaultLoot(BlockDispenseLootEvent event) {
        List<ItemStack> loot = new ArrayList<>(event.getDispensedLoot());
        if (loot.removeIf(limits::isBanned)) {
            event.setDispensedLoot(loot);
        }
    }

    /** Trial chamber chests, barrels and any other generated loot. */
    @EventHandler
    public void onLoot(LootGenerateEvent event) {
        List<ItemStack> loot = new ArrayList<>(event.getLoot());
        if (loot.removeIf(limits::isBanned)) {
            event.setLoot(loot);
        }
    }

    // ---------------------------------------------------------------- utils

    private static boolean canBypass(Player player) {
        return player.hasPermission(BYPASS_PERMISSION);
    }

    private static void deny(Player player, String message) {
        player.sendActionBar(Component.text(message, NamedTextColor.RED));
    }
}
