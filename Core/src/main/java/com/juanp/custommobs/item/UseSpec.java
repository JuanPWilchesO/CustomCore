package com.juanp.custommobs.item;

/**
 * Lo que hace un objeto del catalogo al usarse ({@code usar:} en su yml).
 *
 * <p>Vale para comida (se consume al comer) y para objetos de clic derecho (se gasta una
 * unidad). El efecto sale de {@code effects.yml}: se define una vez y se reutiliza.
 *
 * @param effectId        efecto de {@code effects.yml}
 * @param duration        segundos (0 = los del efecto)
 * @param consume         si se gasta una unidad al usarlo
 * @param cooldownSeconds segundos de espera por jugador entre usos
 */
public record UseSpec(String effectId, int duration, boolean consume, int cooldownSeconds) {
}
