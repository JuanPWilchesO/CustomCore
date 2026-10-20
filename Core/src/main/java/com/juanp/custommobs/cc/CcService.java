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
 * Control de masas para PvP: aturdimiento, retorno decreciente e inmunidad.
 *
 * <p>No se toca el movimiento por paquetes ni se teletransporta: se ponen a cero
 * {@code MOVEMENT_SPEED} y {@code JUMP_STRENGTH} del jugador. El cliente respeta los
 * atributos, asi que el anticheat no ve teletransportes raros. Para mobs se apaga la IA.
 *
 * <p>La duracion y el cooldown salen del yml de la skill; la ventana de DR y la inmunidad
 * salen de {@code config.yml}.
 */
public final class CcService {

    private record Active(String type, long endsAt) {
    }

    private final CustomMobsPlugin plugin;
    private final Map<UUID, Active> active = new ConcurrentHashMap<>();
    private final Map<UUID, Map<String, Deque<Long>>> history = new ConcurrentHashMap<>();
    private final Map<UUID, Long> immuneUntil = new ConcurrentHashMap<>();
    private final Map<UUID, Double> savedSpeed = new ConcurrentHashMap<>();
    private final Map<UUID, Double> savedJump = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> savedAi = new ConcurrentHashMap<>();
    private BukkitTask task;

    public CcService(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    /** {@code true} si el jugador o mob sigue aturdido. */
    public boolean stunned(UUID id) {
        Active current = this.active.get(id);
        return current != null && current.endsAt() > System.currentTimeMillis();
    }

    /**
     * Aplica aturdimiento.
     *
     * @param seconds duracion base; el DR puede recortarla
     * @return {@code false} si la inmunidad o el DR lo bloquearon
     */
    public boolean stun(LivingEntity target, double seconds, LivingEntity source) {
        if (target == null || seconds <= 0.0D) {
            return false;
        }
        long now = System.currentTimeMillis();
        UUID id = target.getUniqueId();
        if (this.immuneUntil.getOrDefault(id, 0L) > now) {
            this.notice(target, this.plugin.config().ccImmuneMessage());
            return false;
        }
        double factor = this.diminishing("stun", id, now);
        if (factor <= 0.0D) {
            this.notice(target, this.plugin.config().ccImmuneMessage());
            return false;
        }
        long ends = now + (long) Math.ceil(seconds * factor * 1000.0D);
        Active previous = this.active.put(id, new Active("stun", ends));
        if (previous == null) {
            this.lock(target);
        }
        target.getWorld().playSound(target.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.5F, 1.4F);
        target.getWorld().spawnParticle(Particle.CRIT,
                target.getLocation().add(0.0D, 1.0D, 0.0D), 20, 0.3D, 0.5D, 0.3D, 0.05D);
        this.notice(target, this.plugin.config().ccStunMessage());
        this.ensureTask();
        return true;
    }

    /**
     * Rompe el control y da inmunidad.
     *
     * @param immunitySeconds segundos de inmunidad; 0 para ninguna
     * @return {@code true} si habia un control activo
     */
    public boolean cleanse(LivingEntity target, double immunitySeconds) {
        if (target == null) {
            return false;
        }
        UUID id = target.getUniqueId();
        Active current = this.active.remove(id);
        if (current != null) {
            this.unlock(target);
        }
        if (immunitySeconds > 0.0D) {
            this.immuneUntil.put(id, System.currentTimeMillis()
                    + (long) Math.ceil(immunitySeconds * 1000.0D));
        }
        this.notice(target, this.plugin.config().ccCleanseMessage());
        return current != null;
    }

    /** Suelta el control sin dar inmunidad; se usa al salir, morir o desactivar. */
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
        for (Map.Entry<UUID, Active> entry : this.active.entrySet()) {
            if (entry.getValue().endsAt() > now) {
                continue;
            }
            this.active.remove(entry.getKey());
            var entity = Bukkit.getEntity(entry.getKey());
            if (entity instanceof LivingEntity living) {
                this.unlock(living);
                int immunity = this.plugin.config().ccImmunitySeconds();
                if (immunity > 0) {
                    this.immuneUntil.put(entry.getKey(), now + immunity * 1000L);
                }
                this.notice(living, this.plugin.config().ccEndMessage());
            }
        }
        this.history.entrySet().removeIf(entry -> {
            long window = this.plugin.config().ccWindowSeconds() * 1000L;
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
