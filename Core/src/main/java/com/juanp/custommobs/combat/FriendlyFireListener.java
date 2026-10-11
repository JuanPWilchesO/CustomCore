package com.juanp.custommobs.combat;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

/**
 * Dos reglas de fuego, en este orden:
 *
 * <ol>
 *   <li><b>Sin fuego amigo.</b> Si el atacado es un mob del bando del atacante —su propio
 *       mob, el de un companero o el de un aliado— el golpe no le hace nada. Los combates
 *       se llenan de golpes cruzados y perder un mob por un espadazo propio es una
 *       tonteria.</li>
 *   <li><b>El dueno ataca, sus mobs van.</b> En modo defensivo, si el dueno golpea a
 *       alguien o algo, sus mobs marcan a ese objetivo como hostil y van a por el, sin
 *       esperar a que nadie les pegue a ellos.</li>
 * </ol>
 */
public final class FriendlyFireListener implements Listener {

    private final MobService service;

    public FriendlyFireListener(MobService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim)) {
            return;
        }
        LivingEntity attacker = attackerOf(event);
        if (attacker == null || victim.getUniqueId().equals(attacker.getUniqueId())) {
            return;
        }
        // Dos jugadores del mismo bando (misma faccion, aliada, o mismo team) no se hacen
        // dano. Solo dentro de los mundos donde trabaja el plugin: fuera de ellos manda el
        // PvP del servidor.
        if (victim instanceof Player defender && attacker instanceof Player aggressor
                && this.service.playerPvpRules(defender.getWorld())
                && this.sameSide(aggressor, defender)) {
            event.setCancelled(true);
            return;
        }
        if (this.blocked(event, victim, attacker)) {
            return;
        }
        this.followOwner(victim, attacker);
    }

    /** {@code true} si los dos jugadores son del mismo bando (faccion o team). */
    private boolean sameSide(Player first, Player second) {
        if (this.service.teamLink().isAlly(first.getUniqueId(), second.getUniqueId())) {
            return true;
        }
        String mine = this.service.factionOf(first);
        return mine != null && this.service.sameSide(mine, this.service.factionOf(second));
    }

    /** {@code true} si el golpe era contra su propio bando y se ha anulado. */
    private boolean blocked(EntityDamageByEntityEvent event, LivingEntity victim, LivingEntity attacker) {
        CustomMob target = this.service.find(victim.getUniqueId()).orElse(null);
        if (target == null || !this.service.isProtectedSide(target, attacker)) {
            return false;
        }
        event.setCancelled(true);
        return true;
    }

    /** Los mobs defensivos del dueno que ataca se apuntan a su objetivo. */
    private void followOwner(LivingEntity victim, LivingEntity attacker) {
        if (!(attacker instanceof Player player)) {
            return;
        }
        // Si el atacado es de su propio bando, no se marca: seria ordenarles atacarse.
        if (victim instanceof Player other
                && this.service.teamLink().isAlly(player.getUniqueId(), other.getUniqueId())) {
            return;
        }
        long duration = this.service.config().aggroDurationMillis();
        for (CustomMob customMob : this.service.active()) {
            if (!player.getUniqueId().equals(customMob.ownerId())
                    || customMob.definition().mountable()
                    || !(customMob.entity() instanceof Mob mob) || !mob.isValid()) {
                continue;
            }
            if (this.modeOf(customMob) != PlayerTargetMode.DEFENSIVE) {
                continue;
            }
            this.service.aggro().mark(mob.getUniqueId(), victim.getUniqueId(), duration);
        }
    }

    /** Modo efectivo contra jugadores: el del mob si lo define, si no el del servidor. */
    private PlayerTargetMode modeOf(CustomMob customMob) {
        PlayerTargetMode override = customMob.definition().playerTargetMode();
        return override != null ? override : this.service.config().playerTargetMode();
    }

    /** Autor de la agresion: jugador o mob custom, tambien por proyectil. */
    private LivingEntity attackerOf(EntityDamageByEntityEvent event) {
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile) {
            source = projectile.getShooter() instanceof Entity shooter ? shooter : null;
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
