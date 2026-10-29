package com.juanp.custommobs.skill;

import java.util.Locale;

/**
 * Un trigger de skill: una via de activacion adicional a {@link SkillMode}.
 *
 * <p>Los triggers son independientes del modo. Una skill puede declarar solo {@code mode},
 * solo {@code trigger:}, o los dos: en ese caso se dispara por las dos vias.
 *
 * <ul>
 *   <li>{@link #ON_HURT}: el mob recibe dano.</li>
 *   <li>{@link #ON_ATTACK}: el mob golpea a alguien, cuerpo a cuerpo o con proyectil.</li>
 *   <li>{@link #ON_DEATH}: el mob muere.</li>
 *   <li>{@link #ON_KILL}: el mob mata a otro (jugador o mob, segun {@code trigger.kill}).</li>
 *   <li>{@link #ON_BLOCK}: el mob esta de pie sobre el bloque de {@code trigger.block}.</li>
 *   <li>{@link #ON_DAYTIME}: la hora del mundo coincide con {@code trigger.daytime}.</li>
 *   <li>{@link #ON_HEALTH}: la vida del mob es igual o menor al {@code trigger.health}.</li>
 * </ul>
 */
public enum SkillEvent {

    ON_HURT("onHurt"),
    ON_ATTACK("onAttack"),
    ON_DEATH("onDeath"),
    ON_KILL("onKill"),
    ON_BLOCK("onBlock"),
    ON_DAYTIME("onDaytime"),
    ON_HEALTH("onHealth");

    private final String label;

    SkillEvent(String label) {
        this.label = label;
    }

    /** Nombre canonico que se usa en los avisos del log y en la documentacion. */
    public String label() {
        return this.label;
    }

    /** {@code true} si se valora en el ciclo periodico (condiciones de estado). */
    public boolean periodic() {
        return this == ON_BLOCK || this == ON_DAYTIME || this == ON_HEALTH;
    }

    /** {@code true} si la dispara un evento del juego. */
    public boolean onEvent() {
        return !this.periodic();
    }

    /**
     * Acepta {@code onHurt}, {@code on_hurt}, {@code on-hurt}, {@code onhurt} y sus
     * equivalentes en espanol; {@code null} si no reconoce el texto.
     */
    public static SkillEvent parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String key = raw.toLowerCase(Locale.ROOT).replace("_", "")
                .replace("-", "").replace(" ", "").trim();
        return switch (key) {
            case "onhurt", "al_ser_golpeado", "alsergolpeado", "herido", "hurt" -> ON_HURT;
            case "onattack", "algolpear", "atacar", "attack" -> ON_ATTACK;
            case "ondeath", "almorir", "muerte", "death" -> ON_DEATH;
            case "onkill", "almatar", "matar", "kill" -> ON_KILL;
            case "onblock", "sobrebloque", "bloque", "block" -> ON_BLOCK;
            case "ondaytime", "dedia", "divino", "dia", "daytime", "day" -> ON_DAYTIME;
            case "onhealth", "vida", "health" -> ON_HEALTH;
            default -> null;
        };
    }
}
