package com.juanp.custommobs.skill;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.Set;

/**
 * Las flechas que el juego acepta como municion.
 *
 * <p>El grupo existe para no repetir la lista en cada listener y para no confundir
 * "es una flecha" con "es la flecha normal". Una flecha con efecto lleva datos de
 * pocion: fabricar un {@code new ItemStack(TIPPED_ARROW)} no la representa.
 */
public final class ArrowTypes {

    /** Las tres que el juego acepta como municion. */
    public static final Set<Material> VANILLA =
            Set.of(Material.ARROW, Material.SPECTRAL_ARROW, Material.TIPPED_ARROW);

    private ArrowTypes() {
    }

    public static boolean isArrow(ItemStack item) {
        return item != null && isArrow(item.getType());
    }

    public static boolean isArrow(Material material) {
        return material == Material.ARROW
                || material == Material.SPECTRAL_ARROW
                || material == Material.TIPPED_ARROW;
    }
}
