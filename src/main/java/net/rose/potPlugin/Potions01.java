package net.rose.potPlugin;

import net.rose.potPlugin.listener.BrewingListener;
import net.rose.potPlugin.recipe.BrewingRecipeRegistrar;
import org.bukkit.plugin.java.JavaPlugin;

public final class Potions01 extends JavaPlugin {

    private BrewingRecipeRegistrar recipeRegistrar;

    @Override
    public void onEnable() {
        this.recipeRegistrar = new BrewingRecipeRegistrar(this);
        this.recipeRegistrar.registerAll();

        getServer().getPluginManager().registerEvents(new BrewingListener(), this);
    }

    @Override
    public void onDisable() {
        if (this.recipeRegistrar != null) {
            this.recipeRegistrar.unregisterAll();
        }
    }
}