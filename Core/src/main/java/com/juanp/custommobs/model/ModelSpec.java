package com.juanp.custommobs.model;

import java.util.Map;

/**
 * Modelo de BetterModel que da cuerpo a un mob (opcional).
 *
 * <p>La entidad de Bukkit sigue existiendo (sigue teniendo vida, dano y objetivos); lo que
 * cambia es lo que ve el cliente: el modelo se dibuja encima y, si se pide, el cuerpo
 * vanilla se oculta. Con {@code disguise} activo en el mismo mob, gana el disfraz y el
 * modelo se ignora.
 *
 * @param enabled         si se aplica el modelo
 * @param name            nombre del modelo en {@code plugins/BetterModel/models/}
 * @param hideBody        si se oculta el cuerpo de la entidad vanilla
 * @param hideEquipment   si se oculta el equipo que lleva la entidad
 * @param hideGlow        si se oculta el contorno de brillo de la entidad
 * @param scale           escala del modelo; {@code null} = la que trae el modelo
 * @param damageAnimation si el modelo reproduce solo la animacion de dano
 * @param damageTint      si el modelo se tine de rojo al recibir dano
 * @param sightTrace      si el modelo solo se dibuja con linea de vision; {@code null} = por defecto
 * @param seat            si el modelo pone el asiento de la montura (hueso con tag SEAT)
 * @param animations      animaciones por evento, con las claves de {@link ModelAnimation}
 */
public record ModelSpec(
        boolean enabled,
        String name,
        boolean hideBody,
        boolean hideEquipment,
        boolean hideGlow,
        Double scale,
        boolean damageAnimation,
        boolean damageTint,
        Boolean sightTrace,
        boolean seat,
        Map<ModelAnimation, AnimationSpec> animations
) {

    public static final ModelSpec NONE = new ModelSpec(
            false, null, true, true, false, null, true, true, null, false, Map.of());

    /** {@code true} si este mob lleva modelo y tiene nombre. */
    public boolean active() {
        return this.enabled && this.name != null && !this.name.isBlank();
    }

    /** Animacion configurada para ese evento, o {@code null} si el mob no la define. */
    public AnimationSpec animation(ModelAnimation event) {
        return this.animations.get(event);
    }
}
