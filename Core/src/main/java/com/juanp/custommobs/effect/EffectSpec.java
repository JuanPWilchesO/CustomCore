package com.juanp.custommobs.effect;

import org.bukkit.attribute.Attribute;

import java.util.List;
import java.util.Map;

/**
 * Un efecto reutilizable: se define en {@code effects.yml} y sirve para una pocion, una skill
 * de mob o el uso de un item.
 *
 * @param id          identificador (la clave del yml)
 * @param name        nombre visible (con colores)
 * @param description lo que hace, para {@code /customcore effects info}
 * @param duration    segundos por defecto
 * @param power       numero propio de la mecanica (ej. robo de vida: 0.25)
 * @param mechanic    que hace CustomCore: congelamiento, caida, robo_vida, sigilo, paso_helado,
 *                    quitar_positivos, quitar_negativos, invulnerabilidad o atributos
 * @param vanilla     efectos de Minecraft que aplica ademas, como "SLOW_FALL" o "SPEED:2"
 * @param type        positivo, negativo o neutro: lo usan las mecanicas de purga
 * @param attributes  atributos que modifica (mecanica 'atributos'); vacio en el resto
 */
public record EffectSpec(String id, String name, String description, int duration,
                         double power, String mechanic, List<String> vanilla, String type,
                         Map<Attribute, Double> attributes) {
}
