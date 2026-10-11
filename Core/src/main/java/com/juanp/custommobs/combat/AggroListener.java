package com.juanp.custommobs.combat;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Convierte una agresion contra el bando protegido en hostilidad del mob.
 *
 * <p>Dispara cuando un jugador ataca al dueno de un mob, a un companero o aliado suyo,
 * o a un mob del mismo bando. Los mobs que esten cerca del agredido reaccionan.
 */
public final class AggroListener implements Listener {

    private final MobService service;
    private final AggroRegistry aggro;

    public AggroListener(MobService service) {
        this.service = service;
        this.aggro = service.aggro();
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        LivingEntity attacker = this.attackerOf(event);
        if (attacker == null || victim.getUniqueId().equals(attacker.getUniqueId())) {
            return;
        }

        long duration = this.service.config().aggroDurationMillis();
        double radius = this.service.config().aggroWatchRadius();

        for (CustomMob customMob : this.service.active()) {
            LivingEntity mob = customMob.entity();
            if (!mob.isValid()) {
                continue;
            }
            if (!mob.getWorld().getName().equals(victim.getWorld().getName())) {
                continue;
            }
            if (mob.getLocation().distance(victim.getLocation()) > radius) {
                continue;
            }
            if (!this.service.isProtectedSide(customMob, victim)) {
                continue;
            }
            // Un golpe entre mobs del mismo bando no cuenta: el fuego amigo lo bloquea, asi que
            // no hay agresion que vengar. Marcarlo dejaba a los aliados apuntandose entre si.
            if (this.friendlyHit(victim, attacker)) {
                continue;
            }
            this.aggro.mark(mob.getUniqueId(), attacker.getUniqueId(), duration);
        }
    }

    /**
     * {@code true} si el que pego es del mismo bando de la victima.
     *
     * <p>Cuenta tanto un mob del mismo bando o aliado como un <b>jugador</b> de una faccion
     * aliada. Es el mismo criterio con el que el fuego amigo decide bloquear el golpe: si el
     * dano no va a contar, tampoco debe quedar la marca de agresion. Cuando esto iba solo con
     * mobs, un aliado humano dejaba la marca puesta y el estado quedaba sucio.
     */
    private boolean friendlyHit(LivingEntity victim, LivingEntity attacker) {
        CustomMob victimMob = this.service.find(victim.getUniqueId()).orElse(null);
        if (victimMob == null) {
            return false;
        }
        String mine = this.service.factionOf(victimMob);
        if (mine == null) {
            return false;
        }
        String theirs;
        if (attacker instanceof Player player) {
            theirs = this.service.factionOf(player);
        } else {
            CustomMob attackerMob = this.service.find(attacker.getUniqueId()).orElse(null);
            theirs = attackerMob == null ? null : this.service.factionOf(attackerMob);
        }
        return this.service.sameSide(mine, theirs);
    }

    /**
     * Autor de la agresion. Cuenta jugadores y mobs custom, tanto cuerpo a cuerpo
     * como por proyectil. Los monstruos de vanilla no generan agresion: ya son
     * objetivo por defecto.
     */
    private LivingEntity attackerOf(EntityDamageByEntityEvent event) {
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile) {
            source = projectile.getShooter() instanceof Entity shooter ? shooter : null;
        }
        if (source == null) {
            return null;
        }
        if (source instanceof Player player) {
            return player;
        }
        if (source instanceof LivingEntity living && this.service.find(living.getUniqueId()).isPresent()) {
            return living;
        }
        return null;
    }
}
