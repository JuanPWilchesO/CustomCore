package com.juanp.custommobs.item;

import org.bukkit.Material;

import java.util.List;
import java.util.Map;

/**
 * Especificacion de un item de equipamiento tal como viene del yml.
 *
 * <p>Hay dos formas, excluyentes entre si:
 *
 * <ul>
 *   <li>{@code material} + el resto de campos: el objeto se arma desde el yml.</li>
 *   <li>{@code itemName}: el <b>nombre</b> de un objeto del catalogo ({@code items/<nombre>.yml}),
 *       guardado antes con {@code /customcore item save}. Es la via para equipar un objeto con
 *       NBT —un encantamiento de otro plugin, por ejemplo—, que no se puede escribir a mano.
 *       En ese caso el objeto del catalogo manda tal cual; solo se le respeta {@code dropChance},
 *       que es cosa del mob y no del objeto.</li>
 * </ul>
 *
 * @param material    material base, o {@code null} si el objeto sale del catalogo
 * @param displayName nombre visible (ya con color) o vacio
 * @param lore        lore (ya con color)
 * @param unbreakable si es irrompible
 * @param glint       forzar el brillo de encantamiento
 * @param enchants    encantamiento -> nivel; la clave admite {@code id}, {@code minecraft:id} o {@code excellentenchants:id}
 * @param dropChance  probabilidad de drop al morir (0 = nunca)
 * @param itemName    nombre en el catalogo, o {@code null} si se arma desde el yml
 */
public record EquipItem(
        Material material,
        String displayName,
        List<String> lore,
        boolean unbreakable,
        boolean glint,
        Map<String, Integer> enchants,
        float dropChance,
        String itemName
) {

    /** {@code true} si el objeto se resuelve por nombre contra el catalogo. */
    public boolean fromCatalog() {
        return this.itemName != null;
    }
}
