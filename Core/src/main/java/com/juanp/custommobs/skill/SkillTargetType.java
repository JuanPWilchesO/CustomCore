package com.juanp.custommobs.skill;

import java.util.Locale;

/**
 * Como se elige el objetivo de una skill.
 *
 * <ul>
 *   <li>{@link #LOOK}: la entidad en la cruz de vista. Para un mob, su objetivo actual.</li>
 *   <li>{@link #RADIUS}: todas las entidades dentro del radio.</li>
 * </ul>
 *
 * <p>Se combina con {@link SkillTarget} (ENEMIES, ALLIES o SELF) para decidir a quien
 * se aplica. Asi una skill de mirada no puede hacer fuego amigo por accidente.
 */
public enum SkillTargetType {

    LOOK,
    RADIUS;

    public static SkillTargetType parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "look", "mirada", "vista", "cursor", "crosshair", "observado", "apuntado" -> LOOK;
            case "radius", "radious", "radio", "area", "área", "rango" -> RADIUS;
            default -> null;
        };
    }
}
