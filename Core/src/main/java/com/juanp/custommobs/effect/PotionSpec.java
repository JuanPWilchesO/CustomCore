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
 * @param faction  faccion que puede usarla, o {@code null}
 */
public record PotionSpec(String id, String name, Material material, String color,
                         String effectId, int duration, String faction,
                         com.juanp.custommobs.item.ItemRecipe recipe) {
}
