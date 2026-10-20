package com.juanp.custommobs.skill;

import java.util.Locale;

/** A quien apunta una skill. */
public enum SkillTarget {

    /** El propio mob. */
    SELF,
    /** El dueno del mob (solo mobs de jugador que lo tengan). */
    OWNER,
    /** La entidad que el mob esta atacando en este momento. */
    TARGET,
    /** Lo que el lanzador tiene en la cruz de vista. Para un jugador, raycast; para un
     *  mob, equivale a su objetivo actual. */
    LOOK,
    /** Enemigos vivos dentro del radio, segun la politica de objetivos del mob. */
    ENEMIES,
    /** Aliados vivos dentro del radio: dueno, companeros de team o de faccion aliada. */
    ALLIES,
    /** La entidad que golpeo el proyectil. Solo tiene sentido con {@code mode: impacto}. */
    IMPACT;

    /** {@code true} si es una relacion valida para {@code target-type}. */
    public boolean isRelation() {
        return this == ENEMIES || this == ALLIES || this == SELF;
    }

    public static SkillTarget parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return switch (raw.toLowerCase(Locale.ROOT).trim()) {
            case "self", "yo", "propio" -> SELF;
            case "owner", "dueno", "dueño" -> OWNER;
            case "target", "objetivo", "blanco" -> TARGET;
            case "look", "mirada", "vista", "cursor", "crosshair", "observado", "apuntado" -> LOOK;
            case "enemies", "enemigos", "enemy" -> ENEMIES;
            case "allies", "aliados", "ally" -> ALLIES;
            case "impacto", "impact", "hit", "golpeado", "golpe", "proyectil" -> IMPACT;
            default -> null;
        };
    }
}
