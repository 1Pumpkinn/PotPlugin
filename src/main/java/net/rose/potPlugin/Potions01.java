package net.rose.potPlugin;

import net.rose.potPlugin.enchantlimits.EnchantLimits;
import net.rose.potPlugin.enchantlimits.EnchantLimitsCommand;
import net.rose.potPlugin.enchantlimits.EnchantLimitsListener;
import net.rose.potPlugin.listener.BrewingListener;
import net.rose.potPlugin.potlimits.PotLimitsCommand;
import net.rose.potPlugin.potlimits.PotLimitsListener;
import net.rose.potPlugin.potlimits.PotionLimits;
import net.rose.potPlugin.util.ExtensionRecipes;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class Potions01 extends JavaPlugin {

    @Override
    public void onEnable() {
        ExtensionRecipes.register(this);

        getServer().getPluginManager().registerEvents(new BrewingListener(), this);

        saveDefaultConfig();
        PotionLimits limits = new PotionLimits(this);
        limits.load();

        PotLimitsListener limitsListener = new PotLimitsListener(this, limits);
        getServer().getPluginManager().registerEvents(limitsListener, this);
        limitsListener.startSweepTask();

        PluginCommand command = getCommand("potlimits");
        if (command != null) {
            command.setExecutor(new PotLimitsCommand(limits, limitsListener));
        }

        EnchantLimits enchantLimits = new EnchantLimits(this);
        enchantLimits.load();

        EnchantLimitsListener enchantListener = new EnchantLimitsListener(this, enchantLimits);
        getServer().getPluginManager().registerEvents(enchantListener, this);
        enchantListener.startSweepTask();

        PluginCommand enchantCommand = getCommand("enchantlimits");
        if (enchantCommand != null) {
            enchantCommand.setExecutor(new EnchantLimitsCommand(enchantLimits, enchantListener));
        }
    }

    @Override
    public void onDisable() {
        ExtensionRecipes.unregister();
    }
}
