package com.juanp.custommobs.skill;

import java.util.Locale;

/**
 * Momento del dia que exige {@code trigger: [onDaytime]}.
 *
 * <p>Se compara con la hora del mundo: la noche va de 13000 a 23000, el mismo criterio que
 * usa la aparicion natural de mobs.
 */
public enum Daytime {

    DAY,
    NIGHT;

    private static final long NIGHT_START = 13000L;
    private static final long NIGHT_END = 23000L;

    /** Acepta nombres en espanol y en ingles; {@code null} si no reconoce el texto. */
    public static Daytime parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "day", "dia" -> DAY;
            case "night", "noche" -> NIGHT;
            default -> null;
        };
    }

    /** {@code true} si la hora del mundo ya es de noche. */
    public static boolean isNight(long time) {
        return time >= NIGHT_START && time <= NIGHT_END;
    }

    /** {@code true} si esta es la mitad del dia que pide este valor. */
    public boolean matches(long time) {
        return isNight(time) == (this == NIGHT);
    }
}
