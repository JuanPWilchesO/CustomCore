package com.juanp.custommobs.model;

import java.util.Locale;

/** Eventos del mob que pueden disparar una animacion del modelo. */
public enum ModelAnimation {

    /** En reposo: la que mas tiempo se ve. */
    IDLE("idle", 0, false),
    /** Al caminar. */
    WALK("walk", 10, false),
    /** Al recibir dano. */
    HURT("hurt", 20, false),
    /** Al golpear a alguien. */
    ATTACK("attack", 30, false),
    /** Al morir. Terminal: no se vuelve al estado despues. */
    DEATH("death", 100, true),
    /** Al aparecer. */
    SPAWN("spawn", 40, false);

    private final String key;
    private final int priority;
    private final boolean terminal;

    ModelAnimation(String key, int priority, boolean terminal) {
        this.key = key;
        this.priority = priority;
        this.terminal = terminal;
    }

    /** Clave del yml. */
    public String key() {
        return this.key;
    }

    /**
     * Prioridad por defecto frente a otras animaciones; gana la mas alta.
     *
     * <p>No todas valen lo mismo: la muerte no debe pisarse nunca, el golpe manda sobre el
     * dano, y el dano sobre lo que estaba en curso (reposo o marcha).
     */
    public int defaultPriority() {
        return this.priority;
    }

    /** {@code true} si es la ultima animacion del mob: despues no se vuelve al estado. */
    public boolean terminal() {
        return this.terminal;
    }

    /**
     * {@code true} si responde a un golpe o al dano, que son eventos puntuales.
     *
     * <p>{@code attack} y {@code hurt} NO son lo mismo: uno es el mob pegando y el otro el mob
     * siendo golpeado. Solo comparten que duran un momento.
     */
    public boolean reactive() {
        return this == ATTACK || this == HURT;
    }

    /** Clave del yml a evento, o {@code null} si no existe. */
    public static ModelAnimation parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.toLowerCase(Locale.ROOT).trim();
        for (ModelAnimation value : values()) {
            if (value.key.equals(key)) {
                return value;
            }
        }
        return null;
    }
}
