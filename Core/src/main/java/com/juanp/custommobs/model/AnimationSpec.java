package com.juanp.custommobs.model;

/**
 * Una animacion del modelo, con los ajustes del {@code AnimationModifier} de BetterModel.
 *
 * @param name     nombre de la animacion dentro del modelo
 * @param once     si se reproduce una sola vez (en vez de quedarse en bucle)
 * @param speed    velocidad de reproduccion; {@code null} = la de la animacion
 * @param start    tiempo de "lerp" de entrada, en ticks
 * @param end      tiempo de "lerp" de salida, en ticks
 * @param priority prioridad frente a otras animaciones; gana la mas alta
 */
public record AnimationSpec(String name, boolean once, Float speed, int start, int end, int priority) {

    public static AnimationSpec of(String name) {
        return new AnimationSpec(name, false, null, 0, 0, 0);
    }

    /**
     * Prioridad efectiva.
     *
     * <p>El yml manda si la define; si no, el evento aporta la suya (muerte > golpe > dano >
     * marcha > reposo).
     */
    public int priorityOr(int fallback) {
        return this.priority > 0 ? this.priority : fallback;
    }
}
