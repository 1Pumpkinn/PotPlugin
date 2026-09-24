package net.rose.potPlugin;

import net.rose.potPlugin.listener.BrewingListener;
import net.rose.potPlugin.util.ExtensionRecipes;
import org.bukkit.plugin.java.JavaPlugin;

public final class Potions01 extends JavaPlugin {

    @Override
    public void onEnable() {
        ExtensionRecipes.register(this);

        getServer().getPluginManager().registerEvents(new BrewingListener(), this);
    }

    @Override
    public void onDisable() {
        ExtensionRecipes.unregister();
    }
}