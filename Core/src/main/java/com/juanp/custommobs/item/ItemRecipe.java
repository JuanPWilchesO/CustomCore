package com.juanp.custommobs.item;

import java.util.List;
import java.util.Map;

/**
 * Receta de un objeto del catalogo, tal como viene de {@code items/<nombre>.yml}.
 *
 * <p>Dos formas, igual que en el yml:
 *
 * <ul>
 *   <li><b>Con forma</b> ({@code forma} + {@code ingredientes} como mapa): el <b>orden
 *       importa</b>. Cada letra de la rejilla apunta a un ingrediente; un espacio es un
 *       hueco, y la misma pieza en otra casilla da otra receta.</li>
 *   <li><b>Sin forma</b> ({@code ingredientes} como lista): da igual donde se ponga cada
 *       cosa.</li>
 * </ul>
 *
 * <p>Un ingrediente es un <b>objeto del catalogo</b> por su nombre, o un <b>material</b>
 * vanilla. Si el nombre existe en el catalogo, manda el objeto (con su NBT).
 *
 * @param amount cuantos objetos salen (1-8)
 * @param shape  filas de la rejilla; vacia si la receta es sin forma
 * @param slots  letra -&gt; ingrediente, solo para las recetas con forma
 * @param loose  ingredientes sueltos, solo para las recetas sin forma
 */
public record ItemRecipe(int amount, List<String> shape, Map<Character, String> slots,
                         List<String> loose) {

    /** {@code true} si la receta tiene forma (el orden importa). */
    public boolean shaped() {
        return !this.shape.isEmpty();
    }
}
