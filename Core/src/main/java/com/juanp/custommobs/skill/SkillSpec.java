package com.juanp.custommobs.skill;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Una skill configurable de un mob, leida de la seccion {@code skills} de su yml.
 *
 * <p>Se activa por dos vias independientes que se suman:
 * <ul>
 *   <li>{@code mode} ({@link SkillMode}): la via de siempre —pasiva, activa, interaccion,
 *       item o impacto—.</li>
 *   <li>{@code trigger} ({@link SkillEvent}): eventos del juego y condiciones de estado
 *       (recibir dano, matar, estar sobre un bloque, vivir de noche...).</li>
 * </ul>
 * Una skill debe declarar al menos una de las dos. Si solo declara triggers, el
 * planificador periodico no la toca: la disparan sus eventos. Si no declara ninguna, es
 * pasiva, como siempre.
 *
 * <p>La pasiva se evalua cada {@code skills.interval-ticks} del config central. Una skill con
 * {@code chance} menor que 1 no siempre se dispara: si falla la tirada, se reintenta en
 * la siguiente evaluacion sin consumir el cooldown.
 *
 * @param id                    identificador unico dentro del mob (cooldowns y logs)
 * @param effect                que hace
 * @param target                a quien apunta (en {@code MESSAGE} se ignora: habla al radio)
 * @param targetType            como se elige: LOOK (cruz de vista) o RADIUS (radio)
 * @param mode                  cuando se dispara por la via de siempre; {@code null} si la
 *                              skill solo se activa por triggers
 * @param events                triggers declarados; vacia si la skill no tiene
 * @param triggerBlock          bloque de {@code onBlock}; {@code null} si no aplica
 * @param daytime               mitad del dia de {@code onDaytime}; {@code null} si no aplica
 * @param health                vida umbral de {@code onHealth} (0 = no aplica)
 * @param healthPercent         si el umbral de vida es una fraccion de la vida maxima
 * @param kill                  que muerte cuenta para {@code onKill}
 * @param range                 radio de busqueda y alcance maximo, en bloques
 * @param cooldownSeconds       segundos minimos entre usos
 * @param chance                probabilidad por evaluacion (0.0 a 1.0; 1.0 = siempre)
 * @param amount                dano o curacion (DAMAGE y HEAL)
 * @param message               texto de MESSAGE, o aviso opcional en las demas
 * @param potion                efecto de pocion (POTION), tal como {@code POISON}
 * @param potionDurationSeconds duracion del efecto (POTION)
 * @param potionAmplifier       nivel del efecto, 0 = nivel I (POTION)
 * @param summon                definicion que invoca (SUMMON), como aparezca en su id
 * @param command               comando a ejecutar (CONSOLE_COMMAND, PLAYER_COMMAND)
 * @param chain                 pasos de la conversacion (CHAIN)
 * @param percent               si {@code amount} es una fraccion de la vida maxima (HEAL)
 * @param block                 bloque que coloca BLOCK_BELOW
 * @param blockRadius           radio en bloques de BLOCK_BELOW (0 = solo el de abajo)
 * @param blockDepth            profundidad hacia abajo de BLOCK_BELOW
 * @param breakBlocks           si EXPLOSION rompe bloques
 * @param throughBlocks         si TELEPORT_BEHIND/BACKSTEP pueden atravesar bloques
 * @param distance              distancia detras del objetivo para TELEPORT_BEHIND/BACKSTEP
 * @param startMessage          aviso al lanzar la skill; {@code null} si no hay
 * @param endMessage            aviso cuando caduca el efecto que aplica; {@code null} si no hay
 * @param cooldownMessage       aviso al intentar usarla en espera; admite {@code {segundos}}
 * @param cancelOnHit           si el control se rompe cuando el objetivo recibe dano
 * @param revealOnAttack        si el sigilo se rompe cuando el jugador hace dano
 * @param messageDisplay        donde se muestra el mensaje: auto, chat o pantalla
 */
public record SkillSpec(
        String id,
        SkillEffect effect,
        SkillTarget target,
        SkillTargetType targetType,
        SkillMode mode,
        Set<SkillEvent> events,
        org.bukkit.Material triggerBlock,
        Daytime daytime,
        double health,
        boolean healthPercent,
        KillFilter kill,
        double range,
        int cooldownSeconds,
        double chance,
        double amount,
        String message,
        String potion,
        int potionDurationSeconds,
        int potionAmplifier,
        String summon,
        String command,
        ChainSpec chain,
        boolean percent,
        org.bukkit.Material block,
        int blockRadius,
        int blockDepth,
        boolean breakBlocks,
        boolean throughBlocks,
        double distance,
        String startMessage,
        String endMessage,
        String cooldownMessage,
        boolean cancelOnHit,
        boolean revealOnAttack,
        MessageDisplay messageDisplay
) {

    /** Distancia minima y maxima para {@code backstep}/{@code teleport_behind}, en bloques. */
    public static final double MIN_DISTANCE = 0.5D;
    public static final double MAX_DISTANCE = 20.0D;

    /** Tope del umbral de {@code trigger.health} cuando se escribe en porcentaje. */
    public static final double MAX_HEALTH_PERCENT = 1.0D;

    public SkillSpec {
        if (events == null || events.isEmpty()) {
            events = Set.of();
        } else {
            events = Collections.unmodifiableSet(EnumSet.copyOf(events));
        }
        kill = kill == null ? KillFilter.DEFAULT : kill;
    }

    public boolean hasMessage() {
        return this.message != null && !this.message.isBlank();
    }

    public boolean hasStartMessage() {
        return this.startMessage != null && !this.startMessage.isBlank();
    }

    public boolean hasEndMessage() {
        return this.endMessage != null && !this.endMessage.isBlank();
    }

    public boolean hasCooldownMessage() {
        return this.cooldownMessage != null && !this.cooldownMessage.isBlank();
    }

    /** {@code true} si declara ese trigger. */
    public boolean hasEvent(SkillEvent event) {
        return this.events.contains(event);
    }

    /** {@code true} si se dispara por al menos un evento. */
    public boolean hasTrigger() {
        return !this.events.isEmpty();
    }

    /** {@code true} si el planificador periodico debe evaluarla. */
    public boolean periodic() {
        return this.mode != null && this.mode.periodic();
    }

    /** {@code true} si un trigger de estado (bloque, hora, vida) puede dispararla. */
    public boolean hasStateTrigger() {
        return this.events.stream().anyMatch(SkillEvent::periodic);
    }
}
