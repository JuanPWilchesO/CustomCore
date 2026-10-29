package com.juanp.custommobs.skill;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobDefinition;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.Material;

import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerTeleportEvent.TeleportCause;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Evalua periodicamente las skills de los mobs custom.
 *
 * <p>Los objetivos se resuelven reutilizando la politica de combate del plugin, de modo
 * que "enemigo" y "aliado" significan lo mismo para las skills que para la IA normal.
 */
public final class SkillService extends BukkitRunnable {

    /** Radio por defecto cuando una skill de MESSAGE no especifica uno util. */
    private static final double DEFAULT_SPEAK_RANGE = 20.0D;

    /** Tope de invocados por uso, para que un yml descuidado no suelte una horda. */
    private static final int MAX_SUMMON = 10;

    /** Radio horizontal de la busqueda de emergencia alrededor del punto ideal. */
    private static final int NEARBY_RADIUS = 2;

    /** Margen vertical, en bloques, de la busqueda de un punto seguro. */
    private static final int HEIGHT_RANGE = 4;

    private final MobService service;
    private final Random random = new Random();

    /** Cooldowns: UUID del mob -> (id de skill -> instante del proximo uso en epoch ms). */
    private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

    public SkillService(MobService service) {
        this.service = service;
    }

    @Override
    public void run() {
        long now = System.currentTimeMillis();
        for (CustomMob customMob : this.service.active()) {
            List<SkillSpec> skills = customMob.definition().skills();
            if (skills.isEmpty()) {
                continue;
            }
            LivingEntity entity = customMob.entity();
            if (entity.isDead() || !entity.isValid()) {
                continue;
            }
            // Las de interaccion solo las dispara un clic derecho; las activas, solo en
            // combate; las pasivas, siempre que el mob este cargado. Una skill que solo
            // declara 'trigger' no se toca aqui: la disparan sus eventos.
            boolean inCombat = this.inCombat(entity);
            for (SkillSpec skill : skills) {
                // Las de item solo las dispara el uso explicito de un objeto. Un mob que
                // las referencie con 'ref:' no debe ejecutarlas por su cuenta.
                if (skill.mode() != null && !skill.mode().periodic()) {
                    continue;
                }
                if (skill.mode() == SkillMode.ACTIVE && !inCombat) {
                    continue;
                }
                if (!skill.periodic()) {
                    // Sin via periodica propia: en el tick NO se evalua, aunque tenga triggers
                    // de estado. Esos se disparan por el evento que los cambia (onHurt) y por
                    // la comprobacion de estado de cada skill, que se hace en fire().
                    continue;
                }
                this.tryUse(customMob, skill, now, null, null, customMob.definition().id());
            }
        }
        // Un mob que ya no existe no debe dejar cooldowns colgando.
        this.cooldowns.keySet().removeIf(id -> this.service.find(id).isEmpty());
        this.fireStateTriggers();
    }

    /**
     * Dispara en el tick las skills que se activan por condicion de estado: {@code onBlock},
     * {@code onDaytime} y {@code onHealth}.
     *
     * <p>{@link #run()} solo evalua las skills con {@code mode} pasiva o activa; este metodo
     * cubre las que se activan por su trigger, para que una skill de solo evento no tenga
     * forma de ejecutarse en el ciclo.
     */
    private void fireStateTriggers() {
        long now = System.currentTimeMillis();
        for (CustomMob customMob : this.service.active()) {
            List<SkillSpec> skills = customMob.definition().skills();
            if (skills.isEmpty()) {
                continue;
            }
            LivingEntity entity = customMob.entity();
            if (entity.isDead() || !entity.isValid()) {
                continue;
            }
            for (SkillSpec skill : skills) {
                if (!skill.hasStateTrigger()) {
                    continue;
                }
                SkillEvent met = this.metStateTrigger(customMob, entity, skill);
                if (met != null) {
                    this.tryUse(customMob, skill, now, null, met, customMob.definition().id());
                } else {
                    // La condicion no se cumple (ya no esta sobre el bloque, ya no es de
                    // noche...): se suelta el cooldown. Asi, al volver a cumplirse, la skill
                    // entra en el acto en vez de esperar a que expire un cooldown viejo.
                    Map<String, Long> perMob = this.cooldowns.get(entity.getUniqueId());
                    if (perMob != null) {
                        perMob.remove(skill.id());
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ triggers

    /**
     * Dispara por evento las skills del mob que declaran ese trigger.
     *
     * <p>El trigger no restringe nada: suma una via de activacion a las que ya tenga la
     * skill por su {@code mode}. El cooldown y la probabilidad son los mismos.
     *
     * @param customMob mob que se activa
     * @param event     trigger que acaba de ocurrir
     * @param other     el otro implicado (quien golpeo, quien murio, quien mato); puede ser
     *                  {@code null}
     */
    public void fire(CustomMob customMob, SkillEvent event, LivingEntity other) {
        if (customMob == null || event == null) {
            return;
        }
        List<SkillSpec> skills = customMob.definition().skills();
        if (skills.isEmpty()) {
            return;
        }
        LivingEntity entity = customMob.entity();
        boolean alive = !entity.isDead() && entity.isValid();
        long now = System.currentTimeMillis();
        // De quien / contra quien, para la traza de depuracion.
        String who = customMob.definition().id() + (other == null ? ""
                : " -> " + other.getName() + " (" + other.getType() + ")");
        for (SkillSpec skill : skills) {
            if (!skill.hasEvent(event)) {
                continue;
            }
            // Para 'onKill' el mob sobrevive y puede actuar con normalidad. Para 'onDeath'
            // esta ya muerto: se aplica sin pasar por la resolucion de objetivos, que exige
            // una entidad viva y un mundo cargado.
            if (event == SkillEvent.ON_DEATH) {
                this.useOnDeath(customMob, skill, other, now, true);
                continue;
            }
            if (!alive) {
                continue;
            }
            if (this.stateTriggerReady(customMob, entity, skill)) {
                this.tryUse(customMob, skill, now, other, event, who);
            } else {
                this.trace(event, who, skill, "no cumple " + this.stateTriggerLabel(skill));
            }
        }
    }

    /**
     * Marca de depuracion: la skill llego a evaluarse por un trigger. Solo escribe con
     * {@code debug: true} en el config.
     *
     * <p>Cuenta el RESULTADO, no la llegada del evento: {@code aplicada}, {@code cooldown},
     * {@code suerte}, {@code sin objetivos} o {@code no cumple <estado>}. Antes el aviso se
     * escribia al llegar el evento y no decia si la skill habia hecho algo.
     */
    private void trace(SkillEvent event, String who, SkillSpec skill, String outcome) {
        if (!this.service.config().debug()) {
            return;
        }
        // En la via periodica ('mode' pasiva/activa) no hay evento: se nombra por su modo.
        String trigger = event != null ? event.label()
                : skill.mode() != null ? "mode " + skill.mode().name()
                : "trigger";
        this.service.plugin().getLogger().info("[skill] " + trigger + " "
                + (who == null ? "?" : who) + ": " + skill.id() + " " + outcome);
    }

    /** Nombre legible del trigger de estado que no se cumple, para la traza. */
    private String stateTriggerLabel(SkillSpec skill) {
        for (SkillEvent event : skill.events()) {
            if (event.periodic()) {
                return switch (event) {
                    case ON_BLOCK -> "onBlock (" + skill.triggerBlock() + ")";
                    case ON_DAYTIME -> "onDaytime (" + skill.daytime() + ")";
                    case ON_HEALTH -> "onHealth (" + (skill.healthPercent()
                            ? (skill.health() * 100.0D) + "%" : skill.health()) + ")";
                    default -> event.label();
                };
            }
        }
        return "sus triggers";
    }

    /**
     * {@code true} si la skill cumple ahora mismo todos sus triggers de estado (bloque, hora
     * y vida). Una skill sin triggers de estado siempre esta lista.
     */
    private boolean stateTriggerReady(CustomMob customMob, LivingEntity entity, SkillSpec skill) {
        return this.metStateTrigger(customMob, entity, skill) != null;
    }

    /**
     * Trigger de estado que se cumple de verdad ahora mismo, o {@code null} si falta alguno.
     *
     * <p>Devuelve cual para poder nombrarlo en la traza: con dos skills de estado en el mismo
     * mob, un aviso generico no dice cual salto.
     */
    private SkillEvent metStateTrigger(CustomMob customMob, LivingEntity entity, SkillSpec skill) {
        SkillEvent met = null;
        for (SkillEvent event : skill.events()) {
            boolean ok = switch (event) {
                case ON_BLOCK -> {
                    if (skill.triggerBlock() == null) {
                        yield true;
                    }
                    Block below = entity.getLocation().subtract(0.0D, 1.0D, 0.0D).getBlock();
                    yield below.getType() == skill.triggerBlock();
                }
                case ON_DAYTIME -> skill.daytime() == null
                        || skill.daytime().matches(entity.getWorld().getTime());
                case ON_HEALTH -> skill.health() > 0.0D && this.healthReached(entity, skill);
                default -> true;
            };
            if (!ok) {
                return null;
            }
            if (event.periodic()) {
                met = event;
            }
        }
        return met;
    }

    /** {@code true} si la vida actual es igual o menor al umbral de {@code trigger.health}. */
    private boolean healthReached(LivingEntity entity, SkillSpec skill) {
        double current = entity.getHealth();
        if (!skill.healthPercent()) {
            return current <= skill.health();
        }
        AttributeInstance maxHealth = entity.getAttribute(Attribute.MAX_HEALTH);
        double max = maxHealth != null ? maxHealth.getValue() : 20.0D;
        // Con porcentaje, el umbral es una fraccion de la vida maxima real: asi funcionan
        // tambien las skills que curan o los mobs con absorcion.
        return max > 0.0D && current / max <= skill.health();
    }

    /**
     * Aplica una skill que se dispara al morir el mob.
     *
     * <p>Los efectos se aplican sobre el propio mob (curacion, pocion, invocacion) o sobre el
     * radio de {@code range}. No hay objetivos que resolver: el mob ya no esta.
     */
    private void useOnDeath(CustomMob customMob, SkillSpec skill, LivingEntity killer, long now,
                            boolean traced) {
        LivingEntity entity = customMob.entity();
        String who = customMob.definition().id()
                + (killer == null ? "" : " a manos de " + killer.getName());
        Map<String, Long> perMob = this.cooldowns.computeIfAbsent(entity.getUniqueId(),
                id -> new ConcurrentHashMap<>());
        Long until = perMob.get(skill.id());
        if (until != null && now < until) {
            this.trace(SkillEvent.ON_DEATH, who, skill, "en cooldown ("
                    + Math.max(0L, (until - now) / 1000L) + "s)");
            return;
        }
        if (skill.chance() < 1.0D && this.random.nextDouble() > skill.chance()) {
            this.trace(SkillEvent.ON_DEATH, who, skill,
                    "falla la tirada de chance (" + skill.chance() + ")");
            return;
        }
        perMob.put(skill.id(), now + skill.cooldownSeconds() * 1000L);
        if (skill.effect() == SkillEffect.MESSAGE) {
            String spoken = this.spoken(customMob, skill);
            for (Player listener : this.listeners(entity, skill.range())) {
                listener.sendMessage(com.juanp.custommobs.mob.Texts.color(spoken));
            }
            this.trace(SkillEvent.ON_DEATH, who, skill, "mensaje enviado");
            return;
        }
        if (skill.effect() == SkillEffect.SUMMON) {
            boolean ok = this.summon(customMob, skill);
            this.trace(SkillEvent.ON_DEATH, who, skill,
                    ok ? "invocacion aplicada" : "sin sitio para invocar");
            return;
        }
        // El resto de efectos van contra el asesino si la skill lo pide, o contra el radio
        // del sitio donde murio el mob (una ultima explosion, hielo, dano en area...).
        Location where = entity.getLocation();
        List<LivingEntity> targets = new ArrayList<>();
        switch (skill.target()) {
            case IMPACT, TARGET, LOOK -> {
                if (killer != null && killer.isValid()
                        && this.withinRange(where, killer, skill.range())) {
                    targets.add(killer);
                }
            }
            case SELF -> targets.add(entity);
            case OWNER -> this.ownerOf(customMob)
                    .filter(owner -> this.withinRange(where, owner, skill.range()))
                    .ifPresent(targets::add);
            case ENEMIES -> {
                for (LivingEntity candidate : this.nearby(where, skill.range())) {
                    if (this.service.policy().isValidTarget(customMob, candidate)) {
                        targets.add(candidate);
                    }
                }
            }
            case ALLIES -> {
                for (LivingEntity candidate : this.nearby(where, skill.range())) {
                    if (this.isAlly(customMob, candidate)) {
                        targets.add(candidate);
                    }
                }
            }
        }
        for (LivingEntity target : targets) {
            this.apply(customMob, skill, target);
        }
        this.trace(SkillEvent.ON_DEATH, who, skill, targets.isEmpty()
                ? "sin objetivos" : "aplicada a " + targets.size() + " objetivo(s)");
    }

    // ------------------------------------------------------------------ ejecucion

    private void tryUse(CustomMob customMob, SkillSpec skill, long now) {
        this.tryUse(customMob, skill, now, null, null, null);
    }

    /**
     * Evalua cooldown, probabilidad y objetivos, y aplica la skill.
     *
     * <p>La via periodica y la de eventos comparten este camino: un trigger que acaba de
     * ocurrir solo suma una evaluacion extra, con el mismo cooldown y la misma
     * probabilidad.
     *
     * @param other el otro implicado en el trigger; {@code null} en la via periodica
     * @param event trigger que la disparo; {@code null} en la via periodica
     * @param who   descripcion del mob y el otro implicado, para la traza
     */
    private void tryUse(CustomMob customMob, SkillSpec skill, long now, LivingEntity other,
                        SkillEvent event, String who) {
        LivingEntity entity = customMob.entity();
        Map<String, Long> perMob = this.cooldowns.computeIfAbsent(entity.getUniqueId(),
                id -> new ConcurrentHashMap<>());
        Long until = perMob.get(skill.id());
        if (until != null && now < until) {
            this.trace(event, who, skill, "en cooldown ("
                    + Math.max(0L, (until - now) / 1000L) + "s)");
            return;
        }
        if (skill.chance() < 1.0D && this.random.nextDouble() > skill.chance()) {
            this.trace(event, who, skill, "falla la tirada de chance (" + skill.chance() + ")");
            return;
        }

        if (skill.effect() == SkillEffect.MESSAGE) {
            List<Player> listeners = this.listeners(entity, skill.range());
            if (listeners.isEmpty()) {
                this.trace(event, who, skill, "sin oyentes en " + skill.range() + " bloques");
                return;
            }
            perMob.put(skill.id(), now + skill.cooldownSeconds() * 1000L);
            String spoken = this.spoken(customMob, skill);
            for (Player listener : listeners) {
                // El texto se colorea aqui: los '&' del yml no los traduce Bukkit solo.
                listener.sendMessage(com.juanp.custommobs.mob.Texts.color(spoken));
            }
            this.trace(event, who, skill, "mensaje enviado a " + listeners.size() + " jugador(es)");
            return;
        }

        // La invocacion no va contra un objetivo: pone copias alrededor del propio mob. Si no
        // encuentra sitio, se reintenta en el siguiente ciclo sin gastar el cooldown.
        if (skill.effect() == SkillEffect.SUMMON) {
            if (!this.summon(customMob, skill)) {
                this.trace(event, who, skill, "sin sitio para invocar");
                return;
            }
            perMob.put(skill.id(), now + skill.cooldownSeconds() * 1000L);
            this.trace(event, who, skill, "invocacion aplicada");
            return;
        }

        List<LivingEntity> targets = this.resolve(customMob, skill, other);
        if (targets.isEmpty()) {
            this.trace(event, who, skill, "sin objetivos");
            return;
        }
        perMob.put(skill.id(), now + skill.cooldownSeconds() * 1000L);
        for (LivingEntity target : targets) {
            this.apply(customMob, skill, target);
        }
        this.trace(event, who, skill, "aplicada a " + targets.size() + " objetivo(s)");
    }

    private List<LivingEntity> resolve(CustomMob customMob, SkillSpec skill) {
        return this.resolve(customMob, skill, null);
    }

    /**
     * Resuelve los objetivos de una skill.
     *
     * @param other el otro implicado en un trigger (quien golpeo, quien murio); {@code null}
     *              en la via periodica. Cuando no es nulo, {@code target: target} y
     *              {@code target: look} lo usan a el: en un {@code onAttack} el objetivo del
     *              mob puede no estar fijado todavia.
     */
    private List<LivingEntity> resolve(CustomMob customMob, SkillSpec skill, LivingEntity other) {
        if (skill.targetType() != null) {
            return this.resolveTyped(customMob, skill, other);
        }
        LivingEntity entity = customMob.entity();
        List<LivingEntity> found = new ArrayList<>();
        switch (skill.target()) {
            case SELF -> found.add(entity);
            // El radio manda tambien para el dueno y para el objetivo atacado. Sin esto, una
            // skill de montura le seguia curando al dueno desde el otro lado del mundo.
            case OWNER -> this.ownerOf(customMob)
                    .filter(owner -> this.inRange(entity, owner, skill.range()))
                    .ifPresent(found::add);
            case TARGET, LOOK -> {
                // Desde un mob, 'look' es lo mismo que su objetivo actual.
                LivingEntity target = other != null ? other
                        : entity instanceof Mob mob ? mob.getTarget() : null;
                if (target != null && !target.getUniqueId().equals(entity.getUniqueId())
                        && this.inRange(entity, target, skill.range())) {
                    found.add(target);
                }
            }
            case ENEMIES -> {
                for (LivingEntity candidate : this.nearby(entity, skill.range())) {
                    if (this.service.policy().isValidTarget(customMob, candidate)) {
                        found.add(candidate);
                    }
                }
            }
            case ALLIES -> {
                for (LivingEntity candidate : this.nearby(entity, skill.range())) {
                    if (this.isAlly(customMob, candidate)) {
                        found.add(candidate);
                    }
                }
            }
        }
        return found;
    }

    /** Resolucion nueva: LOOK (un objetivo filtrado por bando) o RADIUS (area). */
    private List<LivingEntity> resolveTyped(CustomMob customMob, SkillSpec skill,
                                            LivingEntity other) {
        LivingEntity entity = customMob.entity();
        List<LivingEntity> found = new ArrayList<>();
        switch (skill.targetType()) {
            case LOOK -> {
                if (skill.target() == SkillTarget.SELF) {
                    found.add(entity);
                } else {
                    LivingEntity target = other != null ? other
                            : entity instanceof Mob mob ? mob.getTarget() : null;
                    if (target != null && !target.getUniqueId().equals(entity.getUniqueId())) {
                        if (skill.target() == SkillTarget.ENEMIES
                                && this.service.policy().isValidTarget(customMob, target)) {
                            found.add(target);
                        } else if (skill.target() == SkillTarget.ALLIES
                                && this.isAlly(customMob, target)) {
                            found.add(target);
                        }
                    }
                }
            }
            case RADIUS -> {
                if (skill.target() == SkillTarget.SELF) {
                    found.add(entity);
                } else if (skill.target() == SkillTarget.ENEMIES) {
                    for (LivingEntity candidate : this.nearby(entity, skill.range())) {
                        if (this.service.policy().isValidTarget(customMob, candidate)) {
                            found.add(candidate);
                        }
                    }
                } else if (skill.target() == SkillTarget.ALLIES) {
                    for (LivingEntity candidate : this.nearby(entity, skill.range())) {
                        if (this.isAlly(customMob, candidate)) {
                            found.add(candidate);
                        }
                    }
                }
            }
        }
        return found;
    }

    private void apply(CustomMob customMob, SkillSpec skill, LivingEntity target) {
        switch (skill.effect()) {
            case DAMAGE -> target.damage(skill.amount(), customMob.entity());
            case HEAL -> this.heal(target, skill.amount(), skill.percent());
            case IGNITE -> this.ignite(target, skill.amount());
            case FREEZE -> this.freeze(target, skill.amount());
            case BLOCK_BELOW -> this.blockBelow(target, skill);
            case EXPLOSION -> this.explosion(target, skill, customMob.entity());
            case REPAIR_ALL -> this.repairAll(target);
            case TELEPORT_BEHIND -> this.teleportBehind(customMob.entity(), target, skill);
            case BACKSTEP -> {
                if (target.getUniqueId().equals(customMob.entity().getUniqueId())) {
                    this.backstep(customMob.entity(), skill);
                }
            }
            case POTION -> this.potion(customMob.entity(), target, skill);
            case STUN -> this.cc().stun(target, skill.amount(), customMob.entity(), skill.cancelOnHit());
            case ROOT -> this.cc().root(target, skill.amount(), customMob.entity(), skill.cancelOnHit());
            case SILENCE -> this.cc().silence(target, skill.amount(), customMob.entity(), skill.cancelOnHit());
            case DISARM -> this.cc().disarm(target, skill.amount(), customMob.entity(), skill.cancelOnHit());
            case CC_IMMUNE -> this.cc().shield(target, skill.amount());
            case CLEANSE_CC -> this.cc().cleanse(target, skill.amount());
            case CONSOLE_COMMAND -> this.consoleCommand(customMob, skill,
                    target instanceof Player player ? player : this.onlineOwner(customMob));
            case PLAYER_COMMAND -> {
                if (target instanceof Player player) {
                    this.playerCommand(customMob, skill, player);
                }
            }
            case MESSAGE -> {
                // Resuelto aparte: un mensaje no tiene un unico destinatario.
            }
            case SUMMON -> {
                // Resuelto aparte: la invocacion se maneja antes de resolver objetivos.
            }
        }
        // Aviso opcional: en dano o curacion se le dice al jugador afectado.
        if (skill.hasMessage() && target instanceof Player player) {
            this.say(player, this.spoken(customMob, skill, player), skill);
        }
    }

    private void heal(LivingEntity target, double amount, boolean percent) {
        AttributeInstance health = target.getAttribute(Attribute.MAX_HEALTH);
        double max = health != null ? health.getValue() : 20.0D;
        // En modo porcentaje, 'amount' es una fraccion de la vida maxima (0.50 = 50%).
        double value = percent ? max * amount : amount;
        target.setHealth(Math.max(0.0D, Math.min(max, target.getHealth() + value)));
    }

    /** Prende fuego al objetivo. {@code amount} son segundos. */
    private void ignite(LivingEntity target, double seconds) {
        int ticks = (int) Math.max(20L, Math.round(Math.max(1.0D, seconds) * 20.0D));
        target.setFireTicks(Math.max(target.getFireTicks(), ticks));
    }

    /**
     * Congela al objetivo. {@code amount} son segundos.
     *
     * <p>Los freeze ticks dan el estado visual y el dano de congelacion; la lentitud
     * asegura que tambien se note en el movimiento, porque el hielo visual por si solo
     * no siempre ralentiza.
     */
    private void freeze(LivingEntity target, double seconds) {
        int ticks = (int) Math.max(20L, Math.round(Math.max(1.0D, seconds) * 20.0D));
        target.setFreezeTicks(Math.max(target.getFreezeTicks(), ticks));
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS,
                ticks, 1, false, true, true));
    }

    /**
     * Convierte los bloques bajo el objetivo en {@code block}. Salta aire, liquidos y
     * bloques irrompibles; el radio y la profundidad ya vienen limitados del parser.
     */
    private void blockBelow(LivingEntity target, SkillSpec skill) {
        Material material = skill.block();
        if (material == null) {
            return;
        }
        int radius = Math.max(0, Math.min(5, skill.blockRadius()));
        int depth = Math.max(1, Math.min(5, skill.blockDepth()));
        Block base = target.getLocation().getBlock().getRelative(BlockFace.DOWN);
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                for (int dy = 0; dy < depth; dy++) {
                    Block block = base.getRelative(dx, -dy, dz);
                    Material current = block.getType();
                    if (current.isAir() || block.isLiquid()
                            || current == Material.BEDROCK
                            || current == Material.BARRIER
                            || current == Material.END_PORTAL_FRAME) {
                        continue;
                    }
                    block.setType(material, false);
                }
            }
        }
    }

    /**
     * Explosion al lado del objetivo, como un ender crystal. {@code amount} es la potencia
     * (1-10) y {@code break-blocks} decide si rompe terreno.
     */
    private void explosion(LivingEntity target, SkillSpec skill, LivingEntity source) {
        Location location = target.getLocation().add(0, 0.5, 0);
        if (source != null) {
            Vector direction = target.getLocation().toVector()
                    .subtract(source.getLocation().toVector());
            direction.setY(0);
            if (direction.lengthSquared() < 0.01D) {
                direction = target.getLocation().getDirection().setY(0);
            }
            if (direction.lengthSquared() > 0.0D) {
                direction.normalize();
                Vector side = new Vector(-direction.getZ(), 0, direction.getX())
                        .normalize().multiply(0.8D);
                location.add(side);
            }
        }
        float power = (float) Math.max(1.0D, Math.min(10.0D, skill.amount()));
        target.getWorld().createExplosion(location, power, false, skill.breakBlocks(), source);
    }

    /**
     * Coloca al lanzador detras del objetivo y lo orienta hacia el. Busca un punto
     * seguro y, si la config lo pide, consulta las protecciones.
     */
    private void teleportBehind(Entity caster, LivingEntity target, SkillSpec skill) {
        Location destination = this.safeBehind(caster, target, skill.distance(),
                skill.throughBlocks());
        if (destination == null) {
            return;
        }
        double eye = caster instanceof Player ? 1.62D : caster.getHeight() * 0.5D;
        Vector look = target.getEyeLocation().toVector()
                .subtract(destination.clone().add(0, eye, 0).toVector());
        destination.setDirection(look);
        this.teleport(caster, destination);
    }

    /**
     * Retrocede al lanzador y lo deja mirando al punto de origen.
     *
     * <p>Se prueba la distancia pedida y, si no hay hueco, distancias cada vez mas cortas.
     * En cada una se buscan varias alturas y un pequeno radio alrededor, para no depender
     * de que el bloque justo detras este libre.
     */
    private void backstep(Entity caster, SkillSpec skill) {
        Vector facing = caster.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.001D) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        Vector behind = facing.clone().multiply(-1);
        Location from = caster.getLocation();
        Location destination = this.searchSpot(caster, from, facing, skill.distance(),
                skill.throughBlocks(), behind);
        if (destination == null) {
            return;
        }
        double eye = caster instanceof Player ? 1.62D : caster.getHeight() * 0.5D;
        Location origin = from.clone().add(0, eye, 0);
        destination.setDirection(origin.toVector()
                .subtract(destination.clone().add(0, eye, 0).toVector()));
        this.teleport(caster, destination);
    }

    /** Aplica protecciones si la config lo pide y teletransporta. */
    private void teleport(Entity caster, Location destination) {
        if (this.service.plugin().config().teleportRespectProtections()) {
            if (caster instanceof Player player) {
                PlayerTeleportEvent event = new PlayerTeleportEvent(player,
                        player.getLocation(), destination, TeleportCause.PLUGIN);
                this.service.plugin().getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return;
                }
            } else {
                EntityTeleportEvent event = new EntityTeleportEvent(caster,
                        caster.getLocation(), destination);
                this.service.plugin().getServer().getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return;
                }
            }
        }
        caster.teleport(destination);
    }

    /** Punto libre detras del objetivo, o {@code null} si no hay sitio. */
    private Location safeBehind(Entity caster, LivingEntity target, double distance,
                                boolean throughBlocks) {
        if (!target.getWorld().equals(caster.getWorld())) {
            return null;
        }
        Vector facing = target.getLocation().getDirection().setY(0);
        if (facing.lengthSquared() < 0.001D) {
            facing = new Vector(0, 0, 1);
        }
        facing.normalize();
        Vector behind = facing.clone().multiply(-1);
        Location reference = target.getLocation();
        return this.searchSpot(caster, reference, facing, distance, throughBlocks, behind);
    }

    /**
     * Prueba la distancia pedida y, si no hay hueco, distancias cada vez mas cortas.
     * Con distancias largas usa pasos de un bloque para no repetir la busqueda 3D
     * decenas de veces; el ultimo intento siempre es la distancia minima.
     */
    private Location searchSpot(Entity caster, Location reference, Vector facing, double distance,
                                boolean throughBlocks, Vector behind) {
        double max = Math.max(SkillSpec.MIN_DISTANCE,
                Math.min(SkillSpec.MAX_DISTANCE, distance));
        double step = max > 5.0D ? 1.0D : 0.5D;
        for (double d = max; d >= SkillSpec.MIN_DISTANCE; d -= step) {
            Location base = reference.clone().subtract(facing.clone().multiply(d));
            Location destination = this.safeSpot(caster, base, throughBlocks, reference, behind);
            if (destination != null) {
                return destination;
            }
        }
        // Con paso de un bloque, la ultima distancia probada puede ser 1, no 0.5.
        if ((max - SkillSpec.MIN_DISTANCE) % step > 1.0E-6D) {
            Location base = reference.clone()
                    .subtract(facing.clone().multiply(SkillSpec.MIN_DISTANCE));
            return this.safeSpot(caster, base, throughBlocks, reference, behind);
        }
        return null;
    }

    /**
     * Punto seguro mas cercano al ideal.
     *
     * <p>Se prueban varias alturas en la misma columna y, si no hay hueco, un radio
     * pequeno en las tres dimensiones. Nunca se elige un hueco sin suelo solido, con
     * liquidos o con bloques que hacen dano. Con {@code throughBlocks} en {@code false},
     * la linea entre el lanzador y el destino tambien tiene que estar despejada. Si se
     * pasa {@code behind}, el destino tiene que quedar al menos medio bloque detras de
     * {@code reference}: asi la busqueda de emergencia no acaba delante del lanzador.
     */
    private Location safeSpot(Entity caster, Location base, boolean throughBlocks,
                              Location reference, Vector behind) {
        if (caster == null || base == null) {
            return null;
        }
        World world = base.getWorld();
        if (world == null || !world.equals(caster.getWorld())) {
            return null;
        }
        Location origin = caster.getLocation();
        double height = Math.max(0.9D, caster.getHeight());
        Location best = null;
        double bestScore = Double.MAX_VALUE;
        int centerY = base.getBlockY();
        for (int radius = 0; radius <= NEARBY_RADIUS; radius++) {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != radius) {
                        continue;
                    }
                    int x = base.getBlockX() + dx;
                    int z = base.getBlockZ() + dz;
                    double atX = base.getX() + dx;
                    double atZ = base.getZ() + dz;
                    for (int dy = -HEIGHT_RANGE; dy <= HEIGHT_RANGE; dy++) {
                        Location candidate = this.safeAt(world, x, centerY + dy, z,
                                atX, atZ, height);
                        if (candidate == null) {
                            continue;
                        }
                        if (behind != null && reference != null
                                && candidate.toVector().subtract(reference.toVector())
                                .dot(behind) < 0.5D) {
                            continue;
                        }
                        if (candidate.distanceSquared(origin) < 0.25D) {
                            continue;
                        }
                        if (!throughBlocks && !this.hasClearPath(origin, candidate, height)) {
                            continue;
                        }
                        // Terreno llano y despejado: el punto ideal ya es el mejor posible.
                        if (radius == 0 && dx == 0 && dz == 0 && dy == 0
                                && Math.abs(candidate.getY() - base.getY()) < 0.01D) {
                            return candidate;
                        }
                        double score = candidate.distanceSquared(base)
                                + Math.abs(dy) * 0.01D;
                        if (score < bestScore) {
                            bestScore = score;
                            best = candidate;
                        }
                    }
                }
            }
        }
        return best;
    }

    /**
     * Hueco sobre un suelo solido en la columna indicada. Devuelve la ubicacion con los
     * pies sobre la cara superior del suelo, o {@code null} si no cabe el lanzador.
     */
    private Location safeAt(World world, int x, int blockY, int z,
                            double atX, double atZ, double height) {
        if (blockY <= world.getMinHeight() + 1 || blockY >= world.getMaxHeight() - 1) {
            return null;
        }
        Block floor = world.getBlockAt(x, blockY - 1, z);
        if (!isFloor(floor)) {
            return null;
        }
        BoundingBox box = floor.getBoundingBox();
        if (box.getMinY() >= box.getMaxY()) {
            return null;
        }
        double floorTop = box.getMaxY();
        int last = (int) Math.floor(floorTop + height);
        if (last >= world.getMaxHeight()) {
            return null;
        }
        for (int y = blockY; y <= last; y++) {
            Block space = world.getBlockAt(x, y, z);
            if (!space.isPassable() || space.isLiquid() || isHazard(space)) {
                return null;
            }
        }
        Location candidate = new Location(world, atX, floorTop, atZ);
        return world.getWorldBorder().isInside(candidate) ? candidate : null;
    }

    /** Suelo que se puede pisar: no deja pasar, no es liquido y no hace dano. */
    private static boolean isFloor(Block block) {
        return !block.isPassable() && !block.isLiquid() && !isHazard(block);
    }

    /** Bloques que no se deben pisar aunque se pueda estar encima. */
    private static boolean isHazard(Block block) {
        return switch (block.getType()) {
            case LAVA, FIRE, SOUL_FIRE, MAGMA_BLOCK, CACTUS, CAMPFIRE, SOUL_CAMPFIRE,
                 POWDER_SNOW, SWEET_BERRY_BUSH, WITHER_ROSE, POINTED_DRIPSTONE, COBWEB -> true;
            default -> false;
        };
    }

    /**
     * {@code true} si el corredor horizontal entre el lanzador y el destino no cruza
     * bloques solidos. Se mide a la altura del suelo mas alto para permitir escalones y
     * caidas de un bloque, pero no paredes.
     */
    private boolean hasClearPath(Location from, Location to, double height) {
        World world = from.getWorld();
        if (world == null || !world.equals(to.getWorld())) {
            return false;
        }
        double baseY = Math.max(from.getY(), to.getY());
        for (double offset : new double[]{0.6D, Math.max(1.0D, height * 0.75D)}) {
            Vector start = new Vector(from.getX(), baseY + offset, from.getZ());
            Vector delta = new Vector(to.getX(), baseY + offset, to.getZ()).subtract(start);
            double length = delta.length();
            if (length < 0.01D) {
                continue;
            }
            RayTraceResult hit = world.rayTraceBlocks(start.toLocation(world),
                    delta.normalize(), length, FluidCollisionMode.NEVER, true);
            if (hit != null && hit.getHitBlock() != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Repara todos los objetos con durabilidad del inventario del jugador o del equipo
     * del mob. Respeta la proteccion 'reparar' del catalogo.
     */
    private void repairAll(LivingEntity target) {
        if (target instanceof Player player) {
            ItemStack[] contents = player.getInventory().getContents();
            for (ItemStack stack : contents) {
                this.repair(stack);
            }
            player.getInventory().setContents(contents);
            player.updateInventory();
            return;
        }
        EntityEquipment equipment = target.getEquipment();
        if (equipment == null) {
            return;
        }
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            ItemStack stack = equipment.getItem(slot);
            if (this.repair(stack)) {
                equipment.setItem(slot, stack);
            }
        }
    }

    /** {@code true} si el objeto tenia dano y se repara. */
    private boolean repair(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return false;
        }
        if (!(stack.getItemMeta() instanceof Damageable damageable)) {
            return false;
        }
        if (damageable.getDamage() <= 0) {
            return false;
        }
        if (this.service.plugin().drops().catalog()
                .modificationDenied(stack, "reparar")) {
            return false;
        }
        damageable.setDamage(0);
        stack.setItemMeta(damageable);
        return true;
    }

    /**
     * La entidad viva que el jugador tiene en la cruz de vista.
     *
     * <p>Se hace el raycast a mano en vez de usar {@code getTargetEntity}: ese metodo queda
     * limitado por el rango de interaccion del jugador (3 bloques), no por el alcance que
     * pida la skill. Aqui manda el {@code range} de la skill, y un poco de tolerancia
     * lateral para no exigir punteria perfecta.
     */
    private LivingEntity lookingAt(Player caster, double range) {
        Location eye = caster.getEyeLocation();
        Vector direction = eye.getDirection();
        double max = Math.max(1.0D, range);

        // Una pared corta el alcance: no se atraviesan bloques.
        RayTraceResult block = caster.getWorld().rayTraceBlocks(eye, direction, max,
                FluidCollisionMode.NEVER, true);
        if (block != null) {
            max = Math.min(max, block.getHitPosition().distance(eye.toVector()));
        }

        RayTraceResult hit = caster.getWorld().rayTraceEntities(eye, direction, max, 0.35D,
                entity -> entity instanceof LivingEntity
                        && !entity.getUniqueId().equals(caster.getUniqueId())
                        && !entity.isDead() && entity.isValid());
        return hit != null && hit.getHitEntity() instanceof LivingEntity living ? living : null;
    }

    /** {@code true} si la skill aplica un efecto de {@code effects.yml} (y tendra boss bar). */
    private boolean hasPluginEffect(SkillSpec skill) {
        if (skill.effect() != SkillEffect.POTION || skill.potion() == null) {
            return false;
        }
        var effects = this.service.plugin().effects();
        return effects != null && effects.effect(skill.potion()).isPresent();
    }

    private void potion(LivingEntity caster, LivingEntity target, SkillSpec skill) {
        // Un efecto del plugin (effects.yml) manda sobre los de Minecraft, y vale tambien para
        // buffear al propio mob con target: self.
        var effects = this.service.plugin().effects();
        if (effects != null) {
            var custom = effects.effect(skill.potion()).orElse(null);
            if (custom != null) {
                UUID notify = caster instanceof Player player ? player.getUniqueId() : null;
                String end = skill.endMessage();
                if (end != null && caster instanceof Player player) {
                    end = end.replace("{jugador}", player.getName());
                }
                effects.apply(target, custom, skill.potionDurationSeconds(), notify, end,
                        skill.revealOnAttack());
                return;
            }
        }
        PotionEffectType type = this.effectOf(skill.potion());
        if (type == null) {
            return;
        }
        target.addPotionEffect(new PotionEffect(type,
                Math.max(1, skill.potionDurationSeconds()) * 20,
                Math.max(0, skill.potionAmplifier()),
                false, true, true));
    }

    private PotionEffectType effectOf(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        return Registry.EFFECT.get(NamespacedKey.minecraft(raw.toLowerCase(Locale.ROOT).trim()));
    }

    // ------------------------------------------------------------------ interaccion

    /** {@code true} si el mob esta en combate: tiene a alguien a quien atacar. */
    private boolean inCombat(LivingEntity entity) {
        return entity instanceof Mob mob && mob.getTarget() != null;
    }

    /**
     * Muestra el mensaje de una skill segun su regla: dialogo al chat, combate y estados a
     * la pantalla. {@code message-display} permite forzar uno u otro.
     */
    private void say(Player player, String text, SkillSpec skill) {
        if (player == null || text == null || text.isBlank()) {
            return;
        }
        boolean chat = skill.effect() == SkillEffect.MESSAGE
                || skill.effect() == SkillEffect.CHAIN
                || skill.messageDisplay() == MessageDisplay.CHAT;
        if (chat) {
            player.sendMessage(com.juanp.custommobs.mob.Texts.color(text));
            return;
        }
        this.service.plugin().status().notice(player, text,
                org.bukkit.boss.BarColor.WHITE);
    }

    /**
     * Dispara las skills de interaccion de un mob: las que un jugador activa con clic
     * derecho. En {@code MESSAGE} el texto va solo a quien hizo clic, y admite
     * {@code {jugador}} ademas de {@code {mob}}.
     */
    public void interact(CustomMob customMob, Player clicker) {
        List<SkillSpec> skills = customMob.definition().skills();
        if (skills.isEmpty()) {
            return;
        }
        LivingEntity entity = customMob.entity();
        if (entity.isDead() || !entity.isValid()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (SkillSpec skill : skills) {
            if (skill.mode() == SkillMode.INTERACT) {
                this.useInteraction(customMob, skill, clicker, now);
            }
        }
    }

    private void useInteraction(CustomMob customMob, SkillSpec skill, Player clicker, long now) {
        LivingEntity entity = customMob.entity();
        Map<String, Long> perMob = this.cooldowns.computeIfAbsent(entity.getUniqueId(),
                id -> new ConcurrentHashMap<>());
        Long until = perMob.get(skill.id());
        if (until != null && now < until) {
            return;
        }
        if (skill.chance() < 1.0D && this.random.nextDouble() > skill.chance()) {
            return;
        }
        perMob.put(skill.id(), now + skill.cooldownSeconds() * 1000L);
        if (skill.effect() == SkillEffect.MESSAGE) {
            // El dialogo va solo a quien hizo clic, no a todo el que este cerca.
            String text = this.spoken(customMob, skill, clicker);
            if (this.service.config().debug()) {
                this.service.plugin().getLogger().info("[skill] Interaccion de "
                        + customMob.definition().id() + " a " + clicker.getName() + ": " + text);
            }
            clicker.sendMessage(com.juanp.custommobs.mob.Texts.color(text));
            return;
        }
        // Con un clic, el actor es quien hizo clic: los comandos y la invocacion van por ahi,
        // sin pasar por los objetivos declarados.
        switch (skill.effect()) {
            case CONSOLE_COMMAND -> this.consoleCommand(customMob, skill, clicker);
            case PLAYER_COMMAND -> this.playerCommand(customMob, skill, clicker);
            case SUMMON -> this.summon(customMob, skill);
            case CHAIN -> this.service.plugin().chains().start(customMob, clicker, skill);
            default -> {
                // Los demas efectos se resuelven igual que una skill normal: el clic es el gatillo.
                for (LivingEntity target : this.resolve(customMob, skill)) {
                    this.apply(customMob, skill, target);
                }
            }
        }
        if (skill.hasMessage()) {
            this.say(clicker, this.spoken(customMob, skill, clicker), skill);
        }
    }

    // ------------------------------------------------------------------ comandos

    /**
     * Ejecuta el comando de la skill como si lo escribiera la consola.
     *
     * <p>El texto admite {@code {mob}} y {@code {jugador}}: el segundo se sustituye por el
     * jugador que disparo la skill —quien hizo clic, el objetivo, o el dueno—. Si no hay
     * jugador al que referirse, el comando igual sale, con {@code {jugador}} sin sustituir.
     */
    private void consoleCommand(CustomMob customMob, SkillSpec skill, Player player) {
        String text = this.commandText(customMob, skill, player);
        if (text == null) {
            return;
        }
        this.service.plugin().getServer().dispatchCommand(
                this.service.plugin().getServer().getConsoleSender(), text);
    }

    /** Hace que el jugador ejecute el comando de la skill, como si lo hubiera escrito el. */
    private void playerCommand(CustomMob customMob, SkillSpec skill, Player player) {
        if (player == null) {
            return;
        }
        String text = this.commandText(customMob, skill, player);
        if (text == null) {
            return;
        }
        player.performCommand(text);
    }

    /** El comando con los marcadores sustituidos, o {@code null} si la skill no trae ninguno. */
    private String commandText(CustomMob customMob, SkillSpec skill, Player player) {
        if (skill.command() == null || skill.command().isBlank()) {
            return null;
        }
        String text = skill.command().replace("{mob}", customMob.definition().displayName());
        return player == null ? text : text.replace("{jugador}", player.getName());
    }

    private com.juanp.custommobs.cc.CcService cc() {
        return this.service.plugin().cc();
    }

    /** El dueno del mob, si esta conectado; si no, {@code null}. */
    private Player onlineOwner(CustomMob customMob) {
        if (customMob.ownerId() == null) {
            return null;
        }
        Player owner = this.service.plugin().getServer().getPlayer(customMob.ownerId());
        return owner != null && owner.isOnline() ? owner : null;
    }

    // ------------------------------------------------------------------ invocacion

    /**
     * Invoca hasta {@code amount} copias de la definicion {@code summon}, alrededor del mob.
     *
     * <p>Los invocados toman el dueno del invocador: si el que invoca es de un jugador, los
     * suyos tambien. Los mobs de servidor invocan mobs libres.
     *
     * @return {@code true} si aparecio al menos uno
     */
    private boolean summon(CustomMob caster, SkillSpec skill) {
        if (skill.summon() == null || skill.summon().isBlank()) {
            return false;
        }
        MobDefinition definition = this.service.plugin().registry().get(skill.summon()).orElse(null);
        if (definition == null) {
            return false;
        }
        int count = Math.max(1, Math.min(MAX_SUMMON, (int) Math.round(skill.amount())));
        double range = Math.max(1.0D, skill.range());
        LivingEntity entity = caster.entity();
        World world = entity.getWorld();
        Player owner = caster.ownerId() == null
                ? null : this.service.plugin().getServer().getPlayer(caster.ownerId());
        int placed = 0;
        for (int i = 0; i < count; i++) {
            Location spot = this.summonSpot(world, entity.getLocation(), range);
            if (spot == null) {
                continue;
            }
            this.service.spawn(definition, spot, owner);
            placed++;
        }
        return placed > 0;
    }

    /** Un sitio libre cerca del invocador, o {@code null} si no lo encuentra. */
    private Location summonSpot(World world, Location around, double range) {
        for (int tries = 0; tries < 8; tries++) {
            double angle = this.random.nextDouble() * Math.PI * 2.0D;
            double distance = 1.0D + this.random.nextDouble() * Math.max(0.0D, range - 1.0D);
            int x = around.getBlockX() + (int) Math.round(Math.cos(angle) * distance);
            int z = around.getBlockZ() + (int) Math.round(Math.sin(angle) * distance);
            int y = world.getHighestBlockYAt(x, z) + 1;
            Block ground = world.getBlockAt(x, y - 1, z);
            Block air = world.getBlockAt(x, y, z);
            if (!ground.getType().isSolid() || !air.isPassable()) {
                continue;
            }
            return new Location(world, x + 0.5D, y, z + 0.5D);
        }
        return null;
    }

    // ------------------------------------------------------------------ auxiliares

    private Optional<LivingEntity> ownerOf(CustomMob customMob) {
        UUID ownerId = customMob.ownerId();
        if (ownerId == null) {
            return Optional.empty();
        }
        Player owner = this.service.plugin().getServer().getPlayer(ownerId);
        if (owner == null || !owner.isOnline() || owner.isDead()) {
            return Optional.empty();
        }
        return Optional.of(owner);
    }

    /**
     * {@code true} si el objetivo esta dentro del radio de la skill.
     *
     * <p>El cargador siempre deja un radio util (por defecto 8 bloques), asi que en la
     * practica estas skills siempre tienen tope; la guarda de 0 o menos solo protege a quien
     * construya un SkillSpec a mano.
     */
    private boolean inRange(LivingEntity source, LivingEntity target, double range) {
        if (range <= 0.0D) {
            return true;
        }
        if (!source.getWorld().equals(target.getWorld())) {
            return false;
        }
        return source.getLocation().distanceSquared(target.getLocation()) <= range * range;
    }

    /** Igual que {@link #inRange(LivingEntity, LivingEntity, double)}, desde un punto. */
    private boolean withinRange(Location center, LivingEntity target, double range) {
        if (center == null || center.getWorld() == null) {
            return false;
        }
        if (range <= 0.0D) {
            return center.getWorld().equals(target.getWorld());
        }
        if (!center.getWorld().equals(target.getWorld())) {
            return false;
        }
        return center.distanceSquared(target.getLocation()) <= range * range;
    }

    private List<LivingEntity> nearby(LivingEntity entity, double range) {
        List<LivingEntity> found = new ArrayList<>();
        for (Entity candidate : entity.getNearbyEntities(range, range, range)) {
            if (candidate instanceof LivingEntity living && !living.isDead() && living.isValid()) {
                found.add(living);
            }
        }
        return found;
    }

    private List<Player> listeners(LivingEntity entity, double range) {
        double effective = range > 0 ? range : DEFAULT_SPEAK_RANGE;
        List<Player> found = new ArrayList<>();
        for (Entity candidate : entity.getNearbyEntities(effective, effective, effective)) {
            if (candidate instanceof Player player && !player.isDead()) {
                found.add(player);
            }
        }
        return found;
    }

    /** Un aliado: el dueno, un companero de team, o un mob de faccion aliada. */
    private boolean isAlly(CustomMob source, LivingEntity candidate) {
        if (candidate.getUniqueId().equals(source.entity().getUniqueId())) {
            return false;
        }
        if (source.isOwner(candidate.getUniqueId())) {
            return true;
        }
        Optional<CustomMob> other = this.service.find(candidate.getUniqueId());
        if (other.isPresent()) {
            CustomMob ally = other.get();
            if (source.definition().server() && ally.definition().server()) {
                return this.service.factions().isAlly(source.definition().faction(), ally.definition().faction());
            }
            return source.teamId() != null && source.teamId().equals(ally.teamId());
        }
        if (candidate instanceof Player player) {
            return source.ownerId() != null
                    && this.service.teamLink().isAlly(source.ownerId(), player.getUniqueId());
        }
        return false;
    }

    // ------------------------------------------------------------------ skills de item

    /**
     * Lanza skills desde un jugador, en el orden en que las declara el item.
     *
     * <p>El cooldown lo aplica el item: usa el mayor entre su propio 'cooldown' y el
     * 'cooldown-seconds' de las skills que lanza. Tampoco se tira 'chance': un clic
     * consciente no debe fallar al azar.
     */
    public int cast(Player caster, List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        int cast = 0;
        for (String id : ids) {
            SkillSpec skill = this.service.plugin().skillCatalog().get(id).orElse(null);
            if (skill == null) {
                this.service.plugin().getLogger().warning(
                        "El item apunta a una skill que no existe en skills/: " + id);
                continue;
            }
            if (skill.mode() == SkillMode.IMPACT) {
                // Se arma al disparar y se resuelve al impactar: no se castea al usar.
                continue;
            }
            this.castOne(caster, skill);
            cast++;
        }
        return cast;
    }

    private void castOne(Player caster, SkillSpec skill) {
        // La unica puerta para comandos y cadenas desde un item es 'mode: item'.
        boolean commandLike = skill.effect() == SkillEffect.CONSOLE_COMMAND
                || skill.effect() == SkillEffect.PLAYER_COMMAND
                || skill.effect() == SkillEffect.CHAIN;
        if (commandLike && skill.mode() != SkillMode.ITEM) {
            this.service.plugin().getLogger().warning("La skill '" + skill.id()
                    + "' usa " + skill.effect() + " pero no es 'mode: item'; no se ejecuta"
                    + " desde un objeto.");
            return;
        }
        if (skill.effect() == SkillEffect.CHAIN || skill.effect() == SkillEffect.SUMMON) {
            this.service.plugin().getLogger().warning("La skill '" + skill.id() + "' usa "
                    + skill.effect() + "; ese efecto no se admite desde un item.");
            return;
        }
        // Si la skill aplica un efecto del plugin, su boss bar ya informa del inicio: el
        // aviso momentaneo sobra. En las demas skills, 'inicio' se sigue mostrando.
        if (skill.hasStartMessage() && !this.hasPluginEffect(skill)) {
            this.say(caster, skill.startMessage().replace("{jugador}", caster.getName()), skill);
        }
        if (skill.effect() == SkillEffect.CONSOLE_COMMAND) {
            this.consoleCommandFromPlayer(caster, skill);
            return;
        }
        if (skill.effect() == SkillEffect.PLAYER_COMMAND) {
            caster.performCommand(this.commandTextForPlayer(caster, skill));
            return;
        }
        if (skill.effect() == SkillEffect.MESSAGE) {
            String text = this.spokenForPlayer(caster, skill);
            if (text.isBlank()) {
                return;
            }
            double range = skill.range() > 0 ? skill.range() : DEFAULT_SPEAK_RANGE;
            for (Player listener : this.listeners(caster, range)) {
                listener.sendMessage(com.juanp.custommobs.mob.Texts.color(text));
            }
            return;
        }

        for (LivingEntity target : this.playerTargets(caster, skill)) {
            this.applyFromPlayer(caster, skill, target);
        }
    }

    /** Aplica el efecto de una skill de jugador a un objetivo ya resuelto. */
    private void applyFromPlayer(Player caster, SkillSpec skill, LivingEntity target) {
        switch (skill.effect()) {
            case HEAL -> this.heal(target, skill.amount(), skill.percent());
            case DAMAGE -> target.damage(skill.amount(), caster);
            case IGNITE -> this.ignite(target, skill.amount());
            case FREEZE -> this.freeze(target, skill.amount());
            case BLOCK_BELOW -> this.blockBelow(target, skill);
            case EXPLOSION -> this.explosion(target, skill, caster);
            case REPAIR_ALL -> this.repairAll(target);
            case TELEPORT_BEHIND -> this.teleportBehind(caster, target, skill);
            case BACKSTEP -> {
                if (target.getUniqueId().equals(caster.getUniqueId())) {
                    this.backstep(caster, skill);
                }
            }
            case POTION -> this.potion(caster, target, skill);
            case STUN -> this.cc().stun(target, skill.amount(), caster, skill.cancelOnHit());
            case ROOT -> this.cc().root(target, skill.amount(), caster, skill.cancelOnHit());
            case SILENCE -> this.cc().silence(target, skill.amount(), caster, skill.cancelOnHit());
            case DISARM -> this.cc().disarm(target, skill.amount(), caster, skill.cancelOnHit());
            case CC_IMMUNE -> this.cc().shield(target, skill.amount());
            case CLEANSE_CC -> this.cc().cleanse(target, skill.amount());
            default -> {
            }
        }
        if (skill.hasMessage() && target instanceof Player player) {
            player.sendMessage(com.juanp.custommobs.mob.Texts.color(
                    this.spokenForPlayer(caster, skill)));
        }
    }

    // ------------------------------------------------------------------ impacto

    /**
     * Castea una skill de impacto: el origen es el punto donde cayo el proyectil y
     * {@code hit} la entidad golpeada, si la hubo.
     *
     * <p>El lanzador sigue siendo el jugador, para atribuir el dano y resolver los
     * avisos. El cooldown y el aviso de inicio los pone el disparo, no el impacto.
     */
    public void castImpact(Player shooter, SkillSpec skill, Location origin, LivingEntity hit) {
        if (skill.effect() == SkillEffect.CHAIN || skill.effect() == SkillEffect.SUMMON
                || skill.effect() == SkillEffect.CONSOLE_COMMAND
                || skill.effect() == SkillEffect.PLAYER_COMMAND) {
            return;
        }
        if (skill.chance() < 1.0D && this.random.nextDouble() > skill.chance()) {
            return;
        }
        for (LivingEntity target : this.impactTargets(shooter, skill, origin, hit)) {
            this.applyFromPlayer(shooter, skill, target);
        }
    }

    /**
     * Objetivos de una skill de impacto. Sin {@code target-type} es una sola entidad:
     * la golpeada. Con {@code radius}, el area se mide desde el punto de impacto.
     */
    private List<LivingEntity> impactTargets(Player shooter, SkillSpec skill,
                                             Location origin, LivingEntity hit) {
        List<LivingEntity> found = new ArrayList<>();
        boolean area = skill.targetType() == SkillTargetType.RADIUS;
        double range = skill.range() > 0 ? skill.range() : DEFAULT_SPEAK_RANGE;
        switch (skill.target()) {
            case IMPACT -> {
                if (hit != null) {
                    found.add(hit);
                }
            }
            case SELF -> found.add(shooter);
            case ENEMIES -> {
                if (area) {
                    for (LivingEntity candidate : this.nearby(origin, range)) {
                        if (this.isEnemyOfPlayer(shooter, candidate)) {
                            found.add(candidate);
                        }
                    }
                } else if (hit != null && this.isEnemyOfPlayer(shooter, hit)) {
                    found.add(hit);
                }
            }
            case ALLIES -> {
                if (area) {
                    // El lanzador tambien es aliado de si mismo, pero solo si esta dentro
                    // del radio: curar a distancia no debe curar a quien disparo.
                    if (origin != null && shooter.getWorld().equals(origin.getWorld())
                            && shooter.getLocation().distanceSquared(origin) <= range * range) {
                        found.add(shooter);
                    }
                    for (LivingEntity candidate : this.nearby(origin, range)) {
                        if (this.isAllyOfPlayer(shooter, candidate)) {
                            found.add(candidate);
                        }
                    }
                } else if (hit != null && this.isAllyOfPlayer(shooter, hit)) {
                    found.add(hit);
                }
            }
            default -> {
            }
        }
        return found;
    }

    /** Entidades vivas alrededor de un punto (el centro no se incluye). */
    private List<LivingEntity> nearby(Location center, double range) {
        List<LivingEntity> found = new ArrayList<>();
        if (center == null || center.getWorld() == null) {
            return found;
        }
        for (Entity candidate : center.getWorld().getNearbyEntities(center, range, range, range)) {
            if (candidate instanceof LivingEntity living && !living.isDead() && living.isValid()) {
                found.add(living);
            }
        }
        return found;
    }

    private List<LivingEntity> playerTargets(Player caster, SkillSpec skill) {
        if (skill.targetType() != null) {
            return this.playerTargetsTyped(caster, skill);
        }
        List<LivingEntity> found = new ArrayList<>();
        switch (skill.target()) {
            case SELF -> found.add(caster);
            case TARGET, LOOK -> {
                LivingEntity looked = this.lookingAt(caster, skill.range());
                if (looked != null) {
                    found.add(looked);
                }
            }
            case ALLIES -> {
                for (LivingEntity candidate : this.nearby(caster, skill.range())) {
                    if (this.isAllyOfPlayer(caster, candidate)) {
                        found.add(candidate);
                    }
                }
            }
            case ENEMIES -> {
                for (LivingEntity candidate : this.nearby(caster, skill.range())) {
                    if (this.isEnemyOfPlayer(caster, candidate)) {
                        found.add(candidate);
                    }
                }
            }
            // OWNER es un concepto de mob; desde un item apunta al lanzador.
            default -> found.add(caster);
        }
        return found;
    }

    /** Resolucion nueva desde un jugador: LOOK filtrado por bando o RADIUS. */
    private List<LivingEntity> playerTargetsTyped(Player caster, SkillSpec skill) {
        List<LivingEntity> found = new ArrayList<>();
        switch (skill.targetType()) {
            case LOOK -> {
                if (skill.target() == SkillTarget.SELF) {
                    found.add(caster);
                } else {
                    LivingEntity looked = this.lookingAt(caster, skill.range());
                    if (looked != null) {
                        if (skill.target() == SkillTarget.ENEMIES
                                && this.isEnemyOfPlayer(caster, looked)) {
                            found.add(looked);
                        } else if (skill.target() == SkillTarget.ALLIES
                                && this.isAllyOfPlayer(caster, looked)) {
                            found.add(looked);
                        }
                    }
                }
            }
            case RADIUS -> {
                if (skill.target() == SkillTarget.SELF) {
                    found.add(caster);
                } else if (skill.target() == SkillTarget.ENEMIES) {
                    for (LivingEntity candidate : this.nearby(caster, skill.range())) {
                        if (this.isEnemyOfPlayer(caster, candidate)) {
                            found.add(candidate);
                        }
                    }
                } else if (skill.target() == SkillTarget.ALLIES) {
                    for (LivingEntity candidate : this.nearby(caster, skill.range())) {
                        if (this.isAllyOfPlayer(caster, candidate)) {
                            found.add(candidate);
                        }
                    }
                }
            }
        }
        return found;
    }

    /** Aliado de un jugador: su team, su faccion, una aliada, o un mob de los suyos. */
    private boolean isAllyOfPlayer(Player caster, LivingEntity candidate) {
        if (candidate.getUniqueId().equals(caster.getUniqueId())) {
            return false;
        }
        if (candidate instanceof Player other) {
            if (this.service.teamLink().isAlly(caster.getUniqueId(), other.getUniqueId())) {
                return true;
            }
            return this.service.sameSide(this.service.factionOf(caster),
                    this.service.factionOf(other));
        }
        CustomMob other = this.service.find(candidate.getUniqueId()).orElse(null);
        if (other == null) {
            return false;
        }
        if (other.isOwner(caster.getUniqueId())) {
            return true;
        }
        if (other.ownerId() != null
                && this.service.teamLink().isAlly(caster.getUniqueId(), other.ownerId())) {
            return true;
        }
        return this.service.sameSide(this.service.factionOf(caster),
                this.service.factionOf(other));
    }

    /** Enemigo de un jugador: facciones enemigas. Sin faccion no hay enemistad. */
    private boolean isEnemyOfPlayer(Player caster, LivingEntity candidate) {
        if (candidate.getUniqueId().equals(caster.getUniqueId())) {
            return false;
        }
        if (candidate instanceof Player other) {
            if (this.service.teamLink().isAlly(caster.getUniqueId(), other.getUniqueId())) {
                return false;
            }
            String mine = this.service.factionOf(caster);
            String theirs = this.service.factionOf(other);
            return mine != null && theirs != null && this.service.factions().isEnemy(mine, theirs);
        }
        CustomMob other = this.service.find(candidate.getUniqueId()).orElse(null);
        if (other != null) {
            if (other.isOwner(caster.getUniqueId())) {
                return false;
            }
            if (other.ownerId() != null
                    && this.service.teamLink().isAlly(caster.getUniqueId(), other.ownerId())) {
                return false;
            }
            String mine = this.service.factionOf(caster);
            String theirs = this.service.factionOf(other);
            return mine != null && theirs != null && this.service.factions().isEnemy(mine, theirs);
        }
        // Vanilla: los monstruos hostiles son enemigos; el resto no.
        return candidate instanceof org.bukkit.entity.Monster;
    }

    private String spokenForPlayer(Player caster, SkillSpec skill) {
        String message = skill.hasMessage() ? skill.message() : "";
        return message.replace("{mob}", caster.getName())
                .replace("{jugador}", caster.getName());
    }

    private String commandTextForPlayer(Player caster, SkillSpec skill) {
        String command = skill.command() == null ? "" : skill.command();
        return command.replace("{mob}", caster.getName())
                .replace("{jugador}", caster.getName());
    }

    private void consoleCommandFromPlayer(Player caster, SkillSpec skill) {
        String command = this.commandTextForPlayer(caster, skill);
        if (command.isBlank()) {
            return;
        }
        this.service.plugin().getServer().dispatchCommand(
                this.service.plugin().getServer().getConsoleSender(), command);
    }

    /** Texto que dice el mob: {@code {mob}} se sustituye por su nombre visible. */
    private String spoken(CustomMob customMob, SkillSpec skill) {
        return this.spoken(customMob, skill, null);
    }

    /** Igual, pero con {@code {jugador}} disponible cuando hay un interlocutor. */
    private String spoken(CustomMob customMob, SkillSpec skill, Player listener) {
        String message = skill.hasMessage() ? skill.message() : "";
        String text = message.replace("{mob}", customMob.definition().displayName());
        if (listener != null) {
            text = text.replace("{jugador}", listener.getName());
        }
        return text;
    }
}
