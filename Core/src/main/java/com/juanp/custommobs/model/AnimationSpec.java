package com.juanp.custommobs.model;

/**
 * Una animacion del modelo, con los ajustes del {@code AnimationModifier} de BetterModel.
 *
 * @param name   nombre de la animacion dentro del modelo
 * @param once   si se reproduce una sola vez (en vez de quedarse en bucle)
 * @param speed  velocidad de reproduccion; {@code null} = la de la animacion
 * @param start  tiempo de "lerp" de entrada, en ticks
 * @param end    tiempo de "lerp" de salida, en ticks
 */
public record AnimationSpec(String name, boolean once, Float speed, int start, int end) {

    public static AnimationSpec of(String name) {
        return new AnimationSpec(name, false, null, 0, 0);
    }
}
