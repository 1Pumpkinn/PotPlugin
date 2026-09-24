package net.rose.potPlugin.util;

import java.util.Set;
import org.bukkit.Material;
import org.bukkit.potion.PotionType;

public final class BrewingConstants {

    /** 8 minutes, in ticks (20 ticks/sec * 60 sec * 8 min). */
    public static final int EXTENDED_DURATION_TICKS = 9600;

    public static final Material EXTENSION_INGREDIENT = Material.REDSTONE;

    /**
     * Weaving and Infested are the 1.21 Trial Chamber potion types.
     * Vanilla explicitly refuses to accept Redstone/Glowstone on them
     * (they always brew at a fixed 3:00), so this plugin adds a custom
     * PotionMix to let Redstone extend them to 8:00 instead.
     */
    public static final Set<PotionType> EXTENDABLE_TYPES =
            Set.of(PotionType.WEAVING, PotionType.INFESTED, PotionType.STRONG_STRENGTH, PotionType.LONG_STRENGTH, PotionType.LONG_SWIFTNESS);

    public static final Set<Material> POTION_CONTAINERS =
            Set.of(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION);

    private BrewingConstants() {
        throw new UnsupportedOperationException("Constants class");
    }
}