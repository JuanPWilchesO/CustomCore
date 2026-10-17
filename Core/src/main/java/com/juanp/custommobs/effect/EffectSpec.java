package com.juanp.custommobs.effect;

import java.util.List;

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
 *                    quitar_positivos o quitar_negativos
 * @param vanilla     efectos de Minecraft que aplica ademas, como "SLOW_FALL" o "SPEED:2"
 * @param type        positivo, negativo o neutro: lo usan las mecanicas de purga
 */
public record EffectSpec(String id, String name, String description, int duration,
                         double power, String mechanic, List<String> vanilla, String type) {
}
