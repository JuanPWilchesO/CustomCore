package com.juanp.custommobs.effect;

import org.bukkit.Material;

/**
 * Una pocion: un item generado por el plugin que aplica un efecto al beberse.
 *
 * @param id       identificador (la clave del yml)
 * @param name     nombre visible (con colores)
 * @param material material del item (POTION si no se dice otra cosa)
 * @param color    color del liquido (nombre de DyeColor, opcional)
 * @param effectId efecto de {@code effects.yml} que aplica
 * @param duration segundos (0 = los del efecto)
 * @param radius   radio del area al impactar (0 = el del material: 4 arrojadiza, 3 persistente)
 * @param band     a quien alcanza al impactar: aliados, enemigos o todos (vacio = segun el tipo)
 * @param faction  faccion que puede usarla, o {@code null}
 */
public record PotionSpec(String id, String name, Material material, String color,
                         String effectId, int duration, int radius, String band,
                         String faction, String clazz,
                         com.juanp.custommobs.item.ItemRecipe recipe) {

    /** {@code true} si se lanza en vez de beberse. */
    public boolean throwable() {
        return this.material == Material.SPLASH_POTION
                || this.material == Material.LINGERING_POTION;
    }

    /** Radio efectivo al impactar. */
    public double impactRadius() {
        if (this.radius > 0) {
            return this.radius;
        }
        return this.material == Material.LINGERING_POTION ? 3.0D : 4.0D;
    }
}
