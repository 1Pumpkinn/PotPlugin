package net.rose.potPlugin.util;

import java.util.Map;
import java.util.Set;
import org.bukkit.Material;
import org.bukkit.potion.PotionType;

public final class BrewingConstants {

    /** 8 minutes, in ticks (20 ticks/sec * 60 sec * 8 min). Final step of every ladder. */
    public static final int EXTENDED_DURATION_TICKS = 9600;

    public static final Material EXTENSION_INGREDIENT = Material.REDSTONE;

    /**
     * Each redstone brewed onto one of these potions moves it one step up its
     * ladder. The values are the potion's duration AFTER that step, in ticks
     * (written in seconds below). The last entry should be the 8:00 cap.
     *
     * Only vanilla potions that CAN'T be extended with redstone belong here.
     * Weaving/Infested are fixed at 3:00 and Strength II / Swiftness II have
     * no redstone recipe in vanilla. LONG_* types are already 8:00 in vanilla,
     * so they must NOT be listed: they are left completely untouched.
     *
     * Example: STRONG_STRENGTH is 1:30 in vanilla, then
     *   1:30 -> (redstone) 4:00 -> (redstone) 8:00
     */
    public static final Map<PotionType, int[]> EXTENSION_STEPS = Map.of(
            PotionType.WEAVING, seconds(300, 480),          // 3:00 -> 5:00 -> 8:00
            PotionType.INFESTED, seconds(300, 480),         // 3:00 -> 5:00 -> 8:00
            PotionType.STRONG_STRENGTH, seconds(240, 480)   // 1:30 -> 4:00 -> 8:00
    );

    /** Kept for existing code that still reads the set of extendable base types. */
    public static final Set<PotionType> EXTENDABLE_TYPES = EXTENSION_STEPS.keySet();

    public static final Material STRENGTHEN_INGREDIENT = Material.GLOWSTONE_DUST;

    /**
     * Vanilla can't turn a redstone-extended (LONG_*) potion into a level II one.
     * With these mixes: long potion + glowstone -> the level II potion, which comes
     * out at the FINAL step of that type's EXTENSION_STEPS ladder (8:00 by default).
     * The value type must also be a key in EXTENSION_STEPS.
     */
    public static final Map<PotionType, PotionType> LONG_TO_STRONG = Map.of(
            PotionType.LONG_STRENGTH, PotionType.STRONG_STRENGTH
    );

    public static final Set<Material> POTION_CONTAINERS =
            Set.of(Material.POTION, Material.SPLASH_POTION, Material.LINGERING_POTION);

    private BrewingConstants() {
        throw new UnsupportedOperationException("Constants class");
    }

    private static int[] seconds(int... seconds) {
        int[] ticks = new int[seconds.length];
        for (int i = 0; i < seconds.length; i++) {
            ticks[i] = seconds[i] * 20;
        }
        return ticks;
    }
}