package com.juanp.custommobs.cc;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Control de masas para PvP: aturdimiento, raiz, silencio y desarme.
 *
 * <p>Cada control es independiente: se guardan por tipo y conviven. El movimiento solo se
 * bloquea si hay un aturdimiento o una raiz activos. No se teletransporta ni se tocan
 * paquetes: se ponen a cero {@code MOVEMENT_SPEED} y {@code JUMP_STRENGTH}; el cliente
 * respeta los atributos, asi que el anticheat no ve nada raro. A los mobs se les apaga la IA.
 *
 * <p>La duracion y el cooldown salen del yml de la skill; la inmunidad, el escudo y los
 * mensajes salen de {@code config.yml}.
 */
public final class CcService {

    private final CustomMobsPlugin plugin;
    private final Map<UUID, Map<String, Long>> active = new ConcurrentHashMap<>();
    /** Tipo -> si el dano recibido lo rompe. */
    private final Map<UUID, Map<String, Boolean>> breakOnHit = new ConcurrentHashMap<>();
    private final Map<UUID, Long> immuneUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Double> savedSpeed = new ConcurrentHashMap<>();
    private final Map<UUID, Double> savedJump = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> savedAi = new ConcurrentHashMap<>();
    /** Cuando se aplico cada tipo: el golpe que lo trae no lo rompe. */
    private final Map<UUID, Map<String, Long>> appliedAt = new ConcurrentHashMap<>();
    /** Ventana en la que el dano del propio golpe no rompe el control. */
    private static final long BREAK_GRACE_MILLIS = 150L;
    private BukkitTask task;

    public CcService(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean stunned(UUID id) {
        return this.has(id, "stun");
    }

    public boolean rooted(UUID id) {
        return this.has(id, "root");
    }

    public boolean silenced(UUID id) {
        return this.has(id, "silence");
    }

    public boolean disarmed(UUID id) {
        return this.has(id, "disarm");
    }

    /** {@code true} si esa entidad tiene algun control vigente en memoria. */
    public boolean activeOn(UUID id) {
        Map<String, Long> types = this.active.get(id);
        if (types == null || types.isEmpty()) {
            return false;
        }
        long now = System.currentTimeMillis();
        for (Long ends : types.values()) {
            if (ends != null && ends > now) {
                return true;
            }
        }
        return false;
    }

    public boolean has(UUID id, String type) {
        Map<String, Long> types = this.active.get(id);
        if (types == null) {
            return false;
        }
        Long ends = types.get(type);
        return ends != null && ends > System.currentTimeMillis();
    }

    public boolean stun(LivingEntity target, double seconds, LivingEntity source, boolean cancelOnHit) {
        return this.apply(target, "stun", seconds, cancelOnHit);
    }

    public boolean root(LivingEntity target, double seconds, LivingEntity source, boolean cancelOnHit) {
        return this.apply(target, "root", seconds, cancelOnHit);
    }

    public boolean silence(LivingEntity target, double seconds, LivingEntity source, boolean cancelOnHit) {
        return this.apply(target, "silence", seconds, cancelOnHit);
    }

    public boolean disarm(LivingEntity target, double seconds, LivingEntity source, boolean cancelOnHit) {
        return this.apply(target, "disarm", seconds, cancelOnHit);
    }

    /**
     * Aplica un control.
     *
     * @param type    {@code stun}, {@code root}, {@code silence} o {@code disarm}
     * @param seconds duracion base; el DR puede recortarla
     * @return {@code false} si la inmunidad o el DR lo bloquearon
     */
    public boolean apply(LivingEntity target, String type, double seconds, boolean cancelOnHit) {
        if (target == null || type == null || seconds <= 0.0D) {
            return false;
        }
        long now = System.currentTimeMillis();
        UUID id = target.getUniqueId();
        if (this.immuneUntil.getOrDefault(id, 0L) > now) {
            this.notice(target, this.plugin.config().ccImmuneMessage());
            return false;
        }
        // Sin retorno decreciente: cada control dura exactamente lo que diga el yml.
        long ends = now + (long) Math.ceil(seconds * 1000.0D);
        Map<String, Long> types = this.active.computeIfAbsent(id,
                key -> new ConcurrentHashMap<>());
        // El estado real es si los atributos estan guardados, no si el tiempo aun corre:
        // si se mira el reloj, un control que expira entre ticks nunca se desbloquea.
        boolean wasLocked = this.locked(id);
        types.merge(type, ends, Math::max);
        this.appliedAt.computeIfAbsent(id, key -> new ConcurrentHashMap<>()).put(type, now);
        if (cancelOnHit) {
            this.breakOnHit.computeIfAbsent(id, key -> new ConcurrentHashMap<>())
                    .put(type, Boolean.TRUE);
        } else {
            Map<String, Boolean> flags = this.breakOnHit.get(id);
            if (flags != null) {
                flags.remove(type);
            }
        }
        if (!wasLocked && this.moves(type)) {
            this.lock(target);
        }
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5F, 1.4F);
        target.getWorld().spawnParticle(Particle.CRIT,
                target.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.3D, 0.5D, 0.3D, 0.05D);
        if (target instanceof Player player) {
            this.plugin.status().show(player, "cc:" + type, this.messageFor(type),
                    seconds, this.colorFor(type), BarStyle.SOLID, 90, true);
        }
        this.ensureTask();
        return true;
    }

    /**
     * Escudo de inmunidad a controles. No limpia lo que ya tengas encima: solo impide
     * que te apliquen controles nuevos mientras dure.
     *
     * @param seconds duracion; si es 0 se usa {@code cc.shield-seconds} del config
     * @return {@code false} si no se pudo aplicar
     */
    public boolean shield(LivingEntity target, double seconds) {
        if (target == null) {
            return false;
        }
        double duration = seconds > 0.0D ? seconds : this.plugin.config().ccShieldSeconds();
        if (duration <= 0.0D) {
            return false;
        }
        long until = System.currentTimeMillis() + (long) Math.ceil(duration * 1000.0D);
        this.immuneUntil.merge(target.getUniqueId(), until, Math::max);
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.5F, 1.6F);
        target.getWorld().spawnParticle(Particle.END_ROD,
                target.getLocation().add(0.0D, 1.0D, 0.0D), 24, 0.4D, 0.6D, 0.4D, 0.02D);
        if (target instanceof Player player) {
            this.plugin.status().show(player, "cc:shield",
                    this.plugin.config().ccShieldMessage(), duration,
                    BarColor.BLUE, BarStyle.SOLID, 90, true);
        }
        return true;
    }

    /**
     * El dano recibido rompe los controles marcados con {@code cancel-on-hit}.
     * Se llama al recibir dano, no al hacerlo.
     */
    public void onDamage(LivingEntity target) {
        if (target == null) {
            return;
        }
        UUID id = target.getUniqueId();
        Map<String, Boolean> flags = this.breakOnHit.get(id);
        Map<String, Long> types = this.active.get(id);
        if (flags == null || types == null) {
            return;
        }
        long now = System.currentTimeMillis();
        Map<String, Long> applied = this.appliedAt.get(id);
        List<String> brokenTypes = new ArrayList<>();
        for (Map.Entry<String, Boolean> entry : flags.entrySet()) {
            if (!Boolean.TRUE.equals(entry.getValue())) {
                continue;
            }
            // El golpe que trae el control no lo rompe: ProjectileHitEvent va antes que
            // EntityDamageByEntityEvent, asi que sin esta gracia el dano de la misma
            // flecha cancelaba el control en el mismo tick.
            Long at = applied == null ? null : applied.get(entry.getKey());
            if (at != null && now - at < BREAK_GRACE_MILLIS) {
                continue;
            }
            if (types.remove(entry.getKey()) != null) {
                brokenTypes.add(entry.getKey());
            }
        }
        if (brokenTypes.isEmpty()) {
            return;
        }
        if (target instanceof Player player) {
            for (String type : brokenTypes) {
                this.plugin.status().clear(player, "cc:" + type);
            }
        }
        flags.clear();
        if (applied != null) {
            for (String type : brokenTypes) {
                applied.remove(type);
            }
        }
        if (!this.movementLocked(types)) {
            this.unlock(target);
        }
        if (types.isEmpty()) {
            this.active.remove(id);
            this.breakOnHit.remove(id);
            this.appliedAt.remove(id);
        }
        this.notice(target, this.plugin.config().ccBreakMessage());
    }

    /**
     * Rompe todos los controles y da inmunidad.
     *
     * @param immunitySeconds segundos de inmunidad; 0 para ninguna
     * @return {@code true} si habia algun control activo
     */
    public boolean cleanse(LivingEntity target, double immunitySeconds) {
        if (target == null) {
            return false;
        }
        UUID id = target.getUniqueId();
        Map<String, Long> types = this.active.remove(id);
        this.breakOnHit.remove(id);
        this.appliedAt.remove(id);
        // Siempre se intenta soltar: puede quedar un bloqueo huerfano aunque el
        // registro ya se hubiera limpiado.
        this.unlock(target);
        if (target instanceof Player player) {
            this.clearBars(player);
        }
        if (immunitySeconds > 0.0D) {
            this.immuneUntil.put(id, System.currentTimeMillis()
                    + (long) Math.ceil(immunitySeconds * 1000.0D));
        }
        this.notice(target, this.plugin.config().ccCleanseMessage());
        return types != null && !types.isEmpty();
    }

    /** Suelta todos los controles sin dar inmunidad; al salir, morir o desactivar. */
    public void release(LivingEntity target) {
        if (target == null) {
            return;
        }
        UUID id = target.getUniqueId();
        this.active.remove(id);
        this.breakOnHit.remove(id);
        this.appliedAt.remove(id);
        this.unlock(target);
        if (target instanceof Player player) {
            this.clearBars(player);
        }
        this.immuneUntil.remove(id);
    }

    /**
     * Suelta todos los controles antes de apagar el plugin.
     *
     * <p>Se recorren tambien los mobs activos: antes solo se miraban los jugadores
     * conectados y los mobs aturdidos se quedaban con la IA apagada para siempre, porque
     * {@code setAI(false)} viaja en el NBT.
     */
    public void releaseAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.release(player);
        }
        var service = this.plugin.mobs();
        if (service != null) {
            for (LivingEntity mob : service.loadedMobs()) {
                if (this.active.containsKey(mob.getUniqueId())) {
                    this.release(mob);
                }
            }
        }
        this.active.clear();
        this.breakOnHit.clear();
        this.appliedAt.clear();
        this.savedSpeed.clear();
        this.savedJump.clear();
        this.savedAi.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Long>> entry : this.active.entrySet()) {
            Map<String, Long> types = entry.getValue();
            boolean wasLocked = this.locked(entry.getKey());
            types.entrySet().removeIf(e -> e.getValue() <= now);
            Map<String, Boolean> flags = this.breakOnHit.get(entry.getKey());
            if (flags != null) {
                flags.keySet().removeIf(type -> !types.containsKey(type));
            }
            Map<String, Long> applied = this.appliedAt.get(entry.getKey());
            if (applied != null) {
                applied.keySet().removeIf(type -> !types.containsKey(type));
            }
            boolean locked = this.movementLocked(types);
            var entity = Bukkit.getEntity(entry.getKey());
            if (wasLocked && !locked && entity instanceof LivingEntity living) {
                this.unlock(living);
            }
            if (entity == null && this.gone(entry.getKey())) {
                // La entidad ya no existe: no dejar restos. Antes se borraba tambien cuando
                // solo estaba descargada, y el mob aturdido perdia la IA guardada sin que
                // nadie se la devolviera: al recargar su chunk seguia con la IA apagada.
                // Si solo esta descargada, el estado se conserva hasta que vuelva.
                this.savedSpeed.remove(entry.getKey());
                this.savedJump.remove(entry.getKey());
                this.savedAi.remove(entry.getKey());
            }
            if (types.isEmpty()) {
                this.active.remove(entry.getKey());
                this.breakOnHit.remove(entry.getKey());
                this.appliedAt.remove(entry.getKey());
                if (entity instanceof LivingEntity living) {
                    int immunity = this.plugin.config().ccImmunitySeconds();
                    if (immunity > 0) {
                        this.immuneUntil.put(entry.getKey(), now + immunity * 1000L);
                    }
                    this.notice(living, this.plugin.config().ccEndMessage());
                }
            }
        }
    }

    /**
     * {@code true} si la entidad ya no existe, para borrar su estado guardado.
     *
     * <p>Un mob descargado sigue en el {@code player-mobs.yml} (o es un mob de servidor
     * persistente) y volvera a cargarse: conservar su estado es lo correcto. El resto —una
     * entidad muerta, recogida o borrada— se limpia.
     */
    private boolean gone(UUID id) {
        var service = this.plugin.mobs();
        if (service == null) {
            return true;
        }
        // Cupo de jugador: el vinculo sobrevive a la descarga del chunk, asi que si esta, el
        // mob sigue vivo aunque ahora mismo no se pueda enumerar.
        var link = service.playerMobs().byEntity(id);
        if (link.isPresent()) {
            return !link.get().deployed();
        }
        // Mobs de servidor y naturales: no tienen vinculo, se miran por la marca del PDC.
        return service.resolve(id).isEmpty();
    }

    private boolean movementLocked(Map<String, Long> types) {
        long now = System.currentTimeMillis();
        return types.getOrDefault("stun", 0L) > now || types.getOrDefault("root", 0L) > now;
    }

    private boolean moves(String type) {
        return "stun".equals(type) || "root".equals(type);
    }

    private boolean locked(UUID id) {
        return this.savedSpeed.containsKey(id) || this.savedAi.containsKey(id);
    }

    private void lock(LivingEntity target) {
        UUID id = target.getUniqueId();
        if (target instanceof Player player) {
            AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                // putIfAbsent: si ya habia un valor guardado, NO se pisa con el 0 actual.
                Double previous = this.savedSpeed.putIfAbsent(id, speed.getBaseValue());
                if (previous != null && previous <= 0.0D) {
                    this.savedSpeed.put(id, 0.1D);
                }
                speed.setBaseValue(0.0D);
            }
            AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
            if (jump != null) {
                Double previous = this.savedJump.putIfAbsent(id, jump.getBaseValue());
                if (previous != null && previous <= 0.0D) {
                    this.savedJump.put(id, 0.42D);
                }
                jump.setBaseValue(0.0D);
            }
            return;
        }
        if (target instanceof Mob mob) {
            this.savedAi.putIfAbsent(id, mob.hasAI());
            // La marca va ANTES de apagar la IA y en el PDC: setAI(false) se guarda en el
            // NBT, asi que sin ella un mob aturdido al descargarse su chunk —o al apagar el
            // servidor— volvia con la IA apagada para siempre.
            mob.getPersistentDataContainer().set(this.plugin.mobs().keys().aiLocked(),
                    PersistentDataType.BYTE, (byte) 1);
            mob.setAI(false);
        }
        // Fuera de la rama de Mob: un mob de otro plugin puede no implementar Mob y aun asi
        // llevar modelo. Congelar un mob sin modelo no hace nada (el enlace lo ignora).
        var service = this.plugin.mobs();
        if (service != null) {
            // Con el cuerpo vanilla oculto, lo que se ve es el modelo de BetterModel: parar la
            // IA no basta porque el modelo seguiria animando y, al empujarle, volveria a andar.
            service.modelLink().frozen(target, true);
        }
    }

    private void unlock(LivingEntity target) {
        UUID id = target.getUniqueId();
        if (target instanceof Player player) {
            Double speed = this.savedSpeed.remove(id);
            AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null && speedAttr != null) {
                // Nunca restaurar 0: es el valor que puso el propio bloqueo.
                speedAttr.setBaseValue(speed > 0.0D ? speed : 0.1D);
            }
            Double jump = this.savedJump.remove(id);
            AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
            if (jump != null && jumpAttr != null) {
                jumpAttr.setBaseValue(jump > 0.0D ? jump : 0.42D);
            }
            return;
        }
        if (target instanceof Mob mob) {
            Boolean ai = this.savedAi.remove(id);
            mob.getPersistentDataContainer().remove(this.plugin.mobs().keys().aiLocked());
            if (ai != null) {
                mob.setAI(ai);
            }
        }
        var service = this.plugin.mobs();
        if (service != null) {
            // Se suelta SIEMPRE, aunque no hubiera IA guardada: puede quedar el modelo
            // congelado de un bloqueo huerfano.
            service.modelLink().frozen(target, false);
        }
    }

    /**
     * Repara una base de velocidad o salto corrupta en un jugador que ya no tiene control.
     * Se llama al entrar: si el servidor se cerro con un bloqueo a medias, no queda cojo.
     */
    public void repair(Player player) {
        UUID id = player.getUniqueId();
        Map<String, Long> types = this.active.get(id);
        if (types != null && this.movementLocked(types)) {
            return;
        }
        this.savedSpeed.remove(id);
        this.savedJump.remove(id);
        AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null && speed.getBaseValue() <= 0.0001D) {
            speed.setBaseValue(0.1D);
        }
        AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
        if (jump != null && jump.getBaseValue() <= 0.0001D) {
            jump.setBaseValue(0.42D);
        }
    }

    private String messageFor(String type) {
        return switch (type) {
            case "root" -> this.plugin.config().ccRootMessage();
            case "silence" -> this.plugin.config().ccSilenceMessage();
            case "disarm" -> this.plugin.config().ccDisarmMessage();
            default -> this.plugin.config().ccStunMessage();
        };
    }

    private BarColor colorFor(String type) {
        return switch (type) {
            case "root" -> BarColor.GREEN;
            case "silence" -> BarColor.YELLOW;
            case "disarm" -> BarColor.PURPLE;
            default -> BarColor.RED;
        };
    }

    private void clearBars(Player player) {
        for (String type : List.of("stun", "root", "silence", "disarm", "shield")) {
            this.plugin.status().clear(player, "cc:" + type);
        }
    }

    private void notice(LivingEntity target, String message) {
        if (target instanceof Player player && message != null && !message.isBlank()) {
            this.plugin.status().notice(player, message, BarColor.WHITE);
        }
    }

    private void ensureTask() {
        if (this.task == null) {
            this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 5L, 5L);
        }
    }
}
