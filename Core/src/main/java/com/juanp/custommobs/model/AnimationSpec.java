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

    /** Prioridad de las animaciones de golpe, dano y muerte: no deben taparse. */
    public static final int ONE_SHOT_PRIORITY = 10;

    public static AnimationSpec of(String name) {
        return new AnimationSpec(name, false, null, 0, 0, 0);
    }

    /**
     * Prioridad efectiva: las de un solo golpe suben solas para que la de andar, que se
     * reinserta en cada ciclo, no las tape.
     */
    public int effectivePriority() {
        return this.once ? Math.max(this.priority, ONE_SHOT_PRIORITY) : this.priority;
    }
}
