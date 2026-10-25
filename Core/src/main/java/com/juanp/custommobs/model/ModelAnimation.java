package com.juanp.custommobs.model;

import java.util.Locale;

/** Eventos del mob que pueden disparar una animacion del modelo. */
public enum ModelAnimation {

    /** En reposo: la que mas tiempo se ve. */
    IDLE("idle"),
    /** Al caminar. */
    WALK("walk"),
    /** Al golpear a alguien. */
    ATTACK("attack"),
    /** Al recibir dano. */
    HURT("hurt"),
    /** Al morir, antes de desaparecer. */
    DEATH("death"),
    /** Al aparecer. */
    SPAWN("spawn");

    private final String key;

    ModelAnimation(String key) {
        this.key = key;
    }

    /** Clave del yml. */
    public String key() {
        return this.key;
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
