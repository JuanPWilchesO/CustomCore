package com.juanp.custommobs.drop;

import org.bukkit.Material;

import java.util.Random;

/**
 * Una entrada de la tabla de drops de un mob.
 *
 * <p>Hay dos formas, excluyentes entre si:
 *
 * <ul>
 *   <li>{@code material} + {@code min}/{@code max}: un objeto vanilla, escrito a mano.</li>
 *   <li>{@code item}: el <b>nombre</b> de un objeto del catalogo, guardado antes con
 *       {@code /customcore item save <nombre>}. Es la via para objetos con NBT —un
 *       encantamiento de ExcellentEnchants, por ejemplo—, que no se pueden escribir a mano.</li>
 * </ul>
 *
 * @param material  material, o {@code null} si el drop sale del catalogo
 * @param itemName  nombre en el catalogo, o {@code null} si es un material simple
 * @param min       cantidad minima
 * @param max       cantidad maxima
 * @param chance    probabilidad de soltarse (0.0 a 1.0)
 * @param label     texto libre para que el yml sea legible; no afecta al drop
 */
public record DropSpec(Material material, String itemName, int min, int max,
                       double chance, String label) {

    /** {@code true} si el objeto se resuelve por nombre contra el catalogo. */
    public boolean fromCatalog() {
        return this.itemName != null;
    }

    /** Cantidad a soltar, sorteada entre {@code min} y {@code max}. */
    public int rollAmount(Random random) {
        return this.max <= this.min ? this.min : this.min + random.nextInt(this.max - this.min + 1);
    }
}
