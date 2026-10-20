package com.juanp.custommobs.cc;

import com.juanp.custommobs.CustomMobsPlugin;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.Bukkit;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayDeque;
import java.util.Deque;
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
 * <p>La duracion y el cooldown salen del yml de la skill; la ventana de DR, la inmunidad y
 * los mensajes salen de {@code config.yml}.
 */
public final class CcService {

    private final CustomMobsPlugin plugin;
    private final Map<UUID, Map<String, Long>> active = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Deque<Long>>> history = new ConcurrentHashMap<>();
    private final Map<UUID, Long> immuneUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Double> savedSpeed = new ConcurrentHashMap<>();
    private final Map<UUID, Double> savedJump = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> savedAi = new ConcurrentHashMap<>();
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

    public boolean has(UUID id, String type) {
        Map<String, Long> types = this.active.get(id);
        if (types == null) {
            return false;
        }
        Long ends = types.get(type);
        return ends != null && ends > System.currentTimeMillis();
    }

    public boolean stun(LivingEntity target, double seconds, LivingEntity source) {
        return this.apply(target, "stun", seconds);
    }

    public boolean root(LivingEntity target, double seconds, LivingEntity source) {
        return this.apply(target, "root", seconds);
    }

    public boolean silence(LivingEntity target, double seconds, LivingEntity source) {
        return this.apply(target, "silence", seconds);
    }

    public boolean disarm(LivingEntity target, double seconds, LivingEntity source) {
        return this.apply(target, "disarm", seconds);
    }

    /**
     * Aplica un control.
     *
     * @param type    {@code stun}, {@code root}, {@code silence} o {@code disarm}
     * @param seconds duracion base; el DR puede recortarla
     * @return {@code false} si la inmunidad o el DR lo bloquearon
     */
    public boolean apply(LivingEntity target, String type, double seconds) {
        if (target == null || type == null || seconds <= 0.0D) {
            return false;
        }
        long now = System.currentTimeMillis();
        UUID id = target.getUniqueId();
        if (this.immuneUntil.getOrDefault(id, 0L) > now) {
            this.notice(target, this.plugin.config().ccImmuneMessage());
            return false;
        }
        double factor = this.diminishing(type, id, now);
        if (factor <= 0.0D) {
            this.notice(target, this.plugin.config().ccImmuneMessage());
            return false;
        }
        long ends = now + (long) Math.ceil(seconds * factor * 1000.0D);
        Map<String, Long> types = this.active.computeIfAbsent(id,
                key -> new ConcurrentHashMap<>());
        boolean wasLocked = this.movementLocked(types);
        types.merge(type, ends, Math::max);
        if (!wasLocked && this.moves(type)) {
            this.lock(target);
        }
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5F, 1.4F);
        target.getWorld().spawnParticle(Particle.CRIT,
                target.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.3D, 0.5D, 0.3D, 0.05D);
        this.notice(target, this.messageFor(type));
        this.ensureTask();
        return true;
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
        if (types != null && !types.isEmpty()) {
            this.unlock(target);
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
        if (this.active.remove(id) != null) {
            this.unlock(target);
        }
        this.immuneUntil.remove(id);
    }

    /** Restaura a todos los conectados antes de apagar el plugin. */
    public void releaseAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            this.release(player);
        }
        this.active.clear();
        this.savedSpeed.clear();
        this.savedJump.clear();
        this.savedAi.clear();
    }

    private void tick() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Long>> entry : this.active.entrySet()) {
            Map<String, Long> types = entry.getValue();
            boolean wasLocked = this.movementLocked(types);
            types.entrySet().removeIf(e -> e.getValue() <= now);
            boolean locked = this.movementLocked(types);
            var entity = Bukkit.getEntity(entry.getKey());
            if (wasLocked && !locked && entity instanceof LivingEntity living) {
                this.unlock(living);
            }
            if (types.isEmpty()) {
                this.active.remove(entry.getKey());
                if (entity instanceof LivingEntity living) {
                    int immunity = this.plugin.config().ccImmunitySeconds();
                    if (immunity > 0) {
                        this.immuneUntil.put(entry.getKey(), now + immunity * 1000L);
                    }
                    this.notice(living, this.plugin.config().ccEndMessage());
                }
            }
        }
        this.pruneHistory(now);
    }

    private void pruneHistory(long now) {
        long window = Math.max(1, this.plugin.config().ccWindowSeconds()) * 1000L;
        this.history.entrySet().removeIf(entry -> {
            entry.getValue().values().forEach(times -> {
                synchronized (times) {
                    while (!times.isEmpty() && times.peekFirst() < now - window) {
                        times.pollFirst();
                    }
                }
            });
            return entry.getValue().values().stream().allMatch(Deque::isEmpty);
        });
    }

    private boolean movementLocked(Map<String, Long> types) {
        long now = System.currentTimeMillis();
        return types.getOrDefault("stun", 0L) > now || types.getOrDefault("root", 0L) > now;
    }

    private boolean moves(String type) {
        return "stun".equals(type) || "root".equals(type);
    }

    private double diminishing(String type, UUID id, long now) {
        long window = Math.max(1, this.plugin.config().ccWindowSeconds()) * 1000L;
        Map<String, Deque<Long>> byType = this.history.computeIfAbsent(id,
                key -> new ConcurrentHashMap<>());
        Deque<Long> times = byType.computeIfAbsent(type, key -> new ArrayDeque<>());
        synchronized (times) {
            while (!times.isEmpty() && times.peekFirst() < now - window) {
                times.pollFirst();
            }
            var multipliers = this.plugin.config().ccMultipliers();
            int index = Math.min(times.size(), Math.max(0, multipliers.size() - 1));
            double factor = multipliers.isEmpty() ? 1.0D : multipliers.get(index);
            if (factor > 0.0D) {
                times.addLast(now);
            }
            return factor;
        }
    }

    private void lock(LivingEntity target) {
        UUID id = target.getUniqueId();
        if (target instanceof Player player) {
            AttributeInstance speed = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null) {
                this.savedSpeed.put(id, speed.getBaseValue());
                speed.setBaseValue(0.0D);
            }
            AttributeInstance jump = player.getAttribute(Attribute.JUMP_STRENGTH);
            if (jump != null) {
                this.savedJump.put(id, jump.getBaseValue());
                jump.setBaseValue(0.0D);
            }
            return;
        }
        if (target instanceof Mob mob) {
            this.savedAi.put(id, mob.hasAI());
            mob.setAI(false);
        }
    }

    private void unlock(LivingEntity target) {
        UUID id = target.getUniqueId();
        if (target instanceof Player player) {
            Double speed = this.savedSpeed.remove(id);
            AttributeInstance speedAttr = player.getAttribute(Attribute.MOVEMENT_SPEED);
            if (speed != null && speedAttr != null) {
                speedAttr.setBaseValue(speed);
            }
            Double jump = this.savedJump.remove(id);
            AttributeInstance jumpAttr = player.getAttribute(Attribute.JUMP_STRENGTH);
            if (jump != null && jumpAttr != null) {
                jumpAttr.setBaseValue(jump);
            }
            return;
        }
        if (target instanceof Mob mob) {
            Boolean ai = this.savedAi.remove(id);
            if (ai != null) {
                mob.setAI(ai);
            }
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

    private void notice(LivingEntity target, String message) {
        if (target instanceof Player player && message != null && !message.isBlank()) {
            player.sendMessage(Texts.color(message));
        }
    }

    private void ensureTask() {
        if (this.task == null) {
            this.task = Bukkit.getScheduler().runTaskTimer(this.plugin, this::tick, 5L, 5L);
        }
    }
}
