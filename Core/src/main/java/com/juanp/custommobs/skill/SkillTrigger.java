package com.juanp.custommobs.skill;

import java.util.Locale;

/**
 * Cuando entra a funcionar una skill.
 *
 * <ul>
 *   <li>{@link #PASSIVE}: siempre, mientras el mob este cargado. Es el comportamiento de
 *       siempre y el valor por defecto.</li>
 *   <li>{@link #ACTIVE}: solo cuando el mob esta en combate, es decir, cuando tiene a
 *       alguien a quien atacar.</li>
 *   <li>{@link #INTERACT}: cuando un jugador hace clic derecho sobre el mob. Sirve para
 *       dialogos de interaccion.</li>
 *   <li>{@link #ITEM}: cuando un jugador usa un objeto del catalogo. Nunca la ejecuta el
 *       planificador de mobs: solo el uso explicito del item.</li>
 *   <li>{@link #IMPACT}: cuando un proyectil lanzado por el jugador impacta. El planificador
 *       de mobs tampoco la ejecuta: la dispara el proyectil.</li>
 * </ul>
 */
public enum SkillTrigger {

    PASSIVE,
    ACTIVE,
    INTERACT,
    ITEM,
    /** Al impactar un proyectil lanzado por el jugador (flecha, tridente, bola de nieve...). */
    IMPACT;

    /** Valor por defecto cuando el yml no dice nada. */
    public static final SkillTrigger DEFAULT = PASSIVE;

    /** Acepta nombres en espanol y en ingles; {@code null} si no reconoce el texto. */
    public static SkillTrigger parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "pasiva", "pasivo", "passive", "siempre", "always" -> PASSIVE;
            case "activa", "activo", "active", "combate", "combat" -> ACTIVE;
            case "interaccion", "interaction", "clic", "click", "interactuar" -> INTERACT;
            case "item", "objeto", "usar", "use", "uso" -> ITEM;
            case "impacto", "impact", "proyectil", "projectile", "al-impactar",
                    "on-hit", "onhit", "hit" -> IMPACT;
            default -> null;
        };
    }
}
