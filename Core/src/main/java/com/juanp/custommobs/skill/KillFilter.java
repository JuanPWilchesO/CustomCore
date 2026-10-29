package com.juanp.custommobs.skill;

import java.util.Locale;

/**
 * Que muerte cuenta para {@code trigger: [onKill]}.
 *
 * <ul>
 *   <li>{@link #ANY}: cualquier objetivo (jugadores, mobs custom y mobs de vanilla).</li>
 *   <li>{@link #PLAYERS}: solo si muere un jugador.</li>
 *   <li>{@link #MOBS}: solo si muere un mob o un animal, nunca un jugador.</li>
 * </ul>
 */
public enum KillFilter {

    ANY,
    PLAYERS,
    MOBS;

    /** Valor por defecto cuando la skill no dice {@code trigger.kill}. */
    public static final KillFilter DEFAULT = ANY;

    /** Acepta nombres en espanol y en ingles; {@code null} si no reconoce el texto. */
    public static KillFilter parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "cualquiera", "cualquier", "todo", "todos", "any", "all", "todos-los",
                    "everything" -> ANY;
            case "jugador", "jugadores", "player", "players" -> PLAYERS;
            case "mob", "mobs", "monstruo", "monstruos", "criatura", "criaturas",
                    "mob_custom", "custom" -> MOBS;
            default -> null;
        };
    }
}
