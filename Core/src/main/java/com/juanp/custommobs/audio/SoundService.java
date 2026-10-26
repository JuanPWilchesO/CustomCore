package com.juanp.custommobs.audio;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import com.juanp.custommobs.mob.SoundSpec;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;

import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/** Reproduce los sonidos configurados en el yml de cada mob. */
public final class SoundService {

    private final MobService service;
    private final Set<String> unknown = ConcurrentHashMap.newKeySet();

    public SoundService(MobService service) {
        this.service = service;
    }

    /**
     * Reproduce un sonido en una ubicacion, si el spec lo define.
     *
     * <p>Se manda a cada jugador cercano uno a uno, no con {@code world.playSound}: el mob que
     * lo emite esta ENMUDECIDO ({@code setSilent}), y un sonido emitido desde su posicion en el
     * mundo sigue siendo un sonido del mundo, no de la entidad, asi que suena igual. Al
     * mandarlo a cada jugador, no depende del estado de la entidad.
     */
    public void play(Location location, SoundSpec spec) {
        if (spec == null || location == null || location.getWorld() == null) {
            return;
        }
        Sound sound = this.resolve(spec.sound());
        if (sound == null) {
            return;
        }
        for (org.bukkit.entity.Player player : location.getWorld().getPlayers()) {
            if (!player.getWorld().equals(location.getWorld())
                    || player.getLocation().distanceSquared(location)
                            > HEARING_RADIUS * HEARING_RADIUS) {
                continue;
            }
            player.playSound(location, sound, spec.volume(), spec.pitch());
        }
    }

    /** Radio en bloques al que se oye un sonido de mob (el de vanilla ronda los 16). */
    private static final double HEARING_RADIUS = 32.0D;

    /** Reproduce el golpe de un mob custom. */
    public void playAttack(CustomMob attacker) {
        this.play(attacker.entity().getLocation(), attacker.definition().sounds().attack());
    }

    /** Reproduce el sonido de dano de un mob custom. */
    public void playHurt(CustomMob victim) {
        this.play(victim.entity().getLocation(), victim.definition().sounds().hurt());
    }

    /** Reproduce el sonido de muerte de un mob custom. */
    public void playDeath(CustomMob victim) {
        this.play(victim.entity().getLocation(), victim.definition().sounds().death());
    }

    /**
     * Sonido ambiente: suena cada {@code ambientIntervalSeconds} segundos por mob.
     *
     * @param nowMillis instante actual, para no consultar el reloj por cada mob
     */
    public void playAmbient(long nowMillis, java.util.Map<java.util.UUID, Long> last) {
        for (CustomMob customMob : this.service.active()) {
            SoundSpec ambient = customMob.definition().sounds().ambient();
            if (ambient == null) {
                continue;
            }
            long interval = customMob.definition().sounds().ambientIntervalSeconds() * 1000L;
            if (interval <= 0L) {
                continue;
            }
            Long previous = last.get(customMob.entity().getUniqueId());
            if (previous != null && nowMillis - previous < interval) {
                continue;
            }
            last.put(customMob.entity().getUniqueId(), nowMillis);
            this.play(customMob.entity().getLocation(), ambient);
        }
        last.keySet().removeIf(id -> this.service.find(id).isEmpty());
    }

    /** Resuelve la clave del yml a un {@link Sound} de Bukkit. */
    private Sound resolve(String key) {
        String raw = key.toLowerCase(Locale.ROOT).trim();
        try {
            NamespacedKey namespaced = raw.contains(":")
                    ? NamespacedKey.fromString(raw)
                    : NamespacedKey.minecraft(raw);
            if (namespaced != null) {
                Sound sound = Registry.SOUNDS.get(namespaced);
                if (sound != null) {
                    return sound;
                }
            }
        } catch (Throwable ignored) {
            // Servidor sin Registry.SOUNDS: se intenta el nombre clasico.
        }
        try {
            return Sound.valueOf(raw.toUpperCase(Locale.ROOT).replace('.', '_'));
        } catch (Throwable ignored) {
            if (this.unknown.add(raw)) {
                this.service.plugin().getLogger().warning("Sonido desconocido '" + key + "'; se ignora.");
            }
            return null;
        }
    }
}
