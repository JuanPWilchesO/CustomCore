package com.juanp.custommobs.skill;

import java.util.Locale;

/** Que hace una skill cuando se ejecuta. */
public enum SkillEffect {

    /** Aplica dano a cada objetivo. */
    DAMAGE,
    /** Restaura vida a cada objetivo. */
    HEAL,
    /** Prende fuego a cada objetivo durante {@code amount} segundos. */
    IGNITE,
    /** Congela a cada objetivo durante {@code amount} segundos. */
    FREEZE,
    /** Convierte los bloques bajo el objetivo en {@code block}. */
    BLOCK_BELOW,
    /** Explota al lado del objetivo, como un ender crystal. */
    EXPLOSION,
    /** Repara todos los objetos con durabilidad del inventario/equipo del objetivo. */
    REPAIR_ALL,
    /** Teletransporta al lanzador detras del objetivo, mirandolo. */
    TELEPORT_BEHIND,
    /** Retrocede al lanzador mirando al punto de origen. */
    BACKSTEP,
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
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "ignite", "burn", "fuego", "prender", "quemar" -> IGNITE;
            case "freeze", "congelar", "hielo", "helar" -> FREEZE;
            case "block-below", "block_below", "bloque-abajo", "bloque_abajo",
                 "transformar-bloque", "transformar_bloque" -> BLOCK_BELOW;
            case "explosion", "explosión", "end-crystal", "end_crystal",
                 "cristal", "crystal" -> EXPLOSION;
            case "repair-all", "repair_all", "repair", "reparar",
                 "reparar-todo", "reparar_todo" -> REPAIR_ALL;
            case "teleport-behind", "teleport_behind", "detras", "detrás",
                 "emboscada", "behind" -> TELEPORT_BEHIND;
            case "backstep", "back-step", "back_step", "retroceso",
                 "escapar", "escape" -> BACKSTEP;
            default -> {
                try {
                    yield valueOf(raw.toUpperCase(Locale.ROOT).trim());
                } catch (IllegalArgumentException ex) {
                    yield null;
                }
            }
        };
    }
}
