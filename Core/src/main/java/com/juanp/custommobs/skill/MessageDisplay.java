package com.juanp.custommobs.skill;

import java.util.Locale;

/** Donde se muestra el mensaje de una skill. */
public enum MessageDisplay {

    /** Automatico: dialogo al chat; combate y estados, a pantalla. */
    AUTO,
    /** Siempre al chat. */
    CHAT,
    /** Siempre a pantalla (boss bar). */
    SCREEN;

    public static MessageDisplay parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "auto", "automatico", "automático" -> AUTO;
            case "chat" -> CHAT;
            case "screen", "pantalla", "bar", "bossbar" -> SCREEN;
            default -> null;
        };
    }
}
