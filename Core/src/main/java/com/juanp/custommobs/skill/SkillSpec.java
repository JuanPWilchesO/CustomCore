package com.juanp.custommobs.skill;

/**
 * Una skill configurable de un mob, leida de la seccion {@code skills} de su yml.
 *
 * <p>Se evalua cada {@code skills.interval-ticks} del config central. Una skill con
 * {@code chance} menor que 1 no siempre se dispara: si falla la tirada, se reintenta en
 * la siguiente evaluacion sin consumir el cooldown.
 *
 * @param id                    identificador unico dentro del mob (cooldowns y logs)
 * @param effect                que hace
 * @param target                a quien apunta (en {@code MESSAGE} se ignora: habla al radio)
 * @param targetType            como se elige: LOOK (cruz de vista) o RADIUS (radio)
 * @param trigger               cuando se dispara: pasiva, activa (solo en combate) o al
 *                              hacer clic derecho sobre el mob
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
 * @param distance              distancia detras del objetivo para TELEPORT_BEHIND
 */
public record SkillSpec(
        String id,
        SkillEffect effect,
        SkillTarget target,
        SkillTargetType targetType,
        SkillTrigger trigger,
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
        double distance
) {

    public boolean hasMessage() {
        return this.message != null && !this.message.isBlank();
    }
}
