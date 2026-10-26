package com.juanp.custommobs.audio;

import com.juanp.custommobs.mob.MobService;
import com.juanp.custommobs.model.ModelAnimation;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

import java.util.Optional;

/** Traduce los eventos de combate en los sonidos de dano, muerte y ataque. */
public final class SoundListener implements Listener {

    private final MobService service;
    private final SoundService sounds;

    public SoundListener(MobService service, SoundService sounds) {
        this.service = service;
        this.sounds = sounds;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        this.trace(event);
        if (event.getEntity() instanceof LivingEntity victim) {
            this.service.find(victim.getUniqueId()).ifPresent(customMob -> {
                this.sounds.playHurt(customMob);
                // Dano recibido: es 'hurt', no 'attack'. Cada uno tiene su animacion; si el
                // modelo no define 'hurt', no se anima nada (no es un error).
                this.service.modelLink().animate(victim, customMob.definition(), ModelAnimation.HURT);
            });
        }
        LivingEntity attacker = this.attackerOf(event);
        if (attacker != null) {
            this.service.find(attacker.getUniqueId()).ifPresent(customMob -> {
                this.sounds.playAttack(customMob);
                // El mob golpea: 'attack'. Con un arma/proyectil, el que ataca puede no ser el
                // mob; en ese caso attackerOf devuelve a quien disparo.
                this.service.modelLink().animate(attacker, customMob.definition(), ModelAnimation.ATTACK);
            });
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        this.service.find(event.getEntity().getUniqueId()).ifPresent(this.sounds::playDeath);
    }

    /**
     * Traza del golpe con {@code debug: true}.
     *
     * <p>Distingue las tres cosas que pueden pasar: que el mob no golpee, que otro plugin cancele
     * el evento (con {@code ignoreCancelled} no se llega a animar) o que el atacante no sea el mob.
     */
    private void trace(EntityDamageByEntityEvent event) {
        if (!this.service.config().debug()) {
            return;
        }
        LivingEntity attacker = this.attackerOf(event);
        boolean mobIsAttacker = attacker != null
                && this.service.find(attacker.getUniqueId()).isPresent();
        if (!mobIsAttacker && this.service.find(event.getEntity().getUniqueId()).isEmpty()) {
            return;
        }
        this.service.plugin().getLogger().info("[modelo] golpe: atacante="
                + (attacker == null ? "ninguno" : attacker.getType() + (mobIsAttacker ? " (mob)" : ""))
                + " victima=" + event.getEntity().getType()
                + " cancelado=" + event.isCancelled()
                + " causa=" + event.getCause());
    }

    private LivingEntity attackerOf(EntityDamageByEntityEvent event) {
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile) {
            source = projectile.getShooter() instanceof Entity shooter ? shooter : null;
        }
        return source instanceof LivingEntity living ? living : null;
    }
}
