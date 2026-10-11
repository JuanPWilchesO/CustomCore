package com.juanp.custommobs.skill;

import java.util.Locale;

/** Que hace una skill cuando se ejecuta. */
public enum SkillEffect {

    /** Aplica dano a cada objetivo. */
    DAMAGE,
    /** Restaura vida a cada objetivo. */
    HEAL,
    /** El mob habla: el texto va a los jugadores dentro del radio. */
    MESSAGE,
    /** Aplica un efecto de pocion a cada objetivo. */
    POTION,
    /** Invoca copias de otra definicion alrededor del mob. */
    SUMMON,
    /** Ejecuta un comando desde la consola. */
    CONSOLE_COMMAND,
    /** Hace que el jugador ejecute un comando, como si lo escribiera el. */
    PLAYER_COMMAND,
    /** Cadena de interaccion: una conversacion por pasos, a base de clics. */
    CHAIN;

    public static SkillEffect parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return valueOf(raw.toUpperCase(Locale.ROOT).trim());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
