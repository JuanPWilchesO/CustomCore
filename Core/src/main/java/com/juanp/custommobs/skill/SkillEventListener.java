package com.juanp.custommobs.skill;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Dispara las skills que declaran un trigger de evento: {@code onHurt}, {@code onAttack},
 * {@code onDeath} y {@code onKill}.
 *
 * <p>Los triggers de estado ({@code onBlock}, {@code onDaytime}, {@code onHealth}) no pasan
 * por aqui: los valora el ciclo periodico de {@link SkillService}.
 *
 * <p>Prioridad alta en el dano: el mob debe reaccionar con el dano final, despues de que
 * otros listeners del combate lo hayan ajustado, pero antes de que el mob pueda morir por
 * ese mismo golpe.
 */
public final class SkillEventListener implements Listener {

    private final MobService service;
    private final SkillService skills;

    public SkillEventListener(MobService service, SkillService skills) {
        this.service = service;
        this.skills = skills;
    }

    /**
     * Un solo golpe alimenta dos triggers: {@code onHurt} en la victima y {@code onAttack}
     * en el atacante. Se resuelve una sola vez por evento para no disparar dos veces.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof LivingEntity victim) || event.getFinalDamage() <= 0.0D) {
            return;
        }
        LivingEntity attacker = this.attackerOf(event);
        if (attacker != null && attacker.getUniqueId().equals(victim.getUniqueId())) {
            return;
        }
        CustomMob hurt = this.service.find(victim.getUniqueId()).orElse(null);
        if (hurt != null) {
            this.skills.fire(hurt, SkillEvent.ON_HURT, attacker);
        }
        if (attacker == null) {
            return;
        }
        CustomMob attackerMob = this.service.find(attacker.getUniqueId()).orElse(null);
        if (attackerMob != null) {
            this.skills.fire(attackerMob, SkillEvent.ON_ATTACK, victim);
        }
    }

    /**
     * Un solo evento cierra dos triggers: {@code onDeath} en el que muere y {@code onKill}
     * en quien lo mato.
     *
     * <p>Prioridad normal: antes de que {@link MobService} suelte el vinculo del mob, para
     * que la skill se aplique con la entidad aun registrada y en su sitio.
     */
    @EventHandler(priority = EventPriority.NORMAL)
    public void onDeath(EntityDeathEvent event) {
        LivingEntity dead = event.getEntity();
        LivingEntity killer = dead.getKiller();
        CustomMob victim = this.service.find(dead.getUniqueId())
                .or(() -> this.service.read(dead))
                .orElse(null);
        if (victim != null) {
            this.skills.fire(victim, SkillEvent.ON_DEATH, killer);
        }
        if (killer == null) {
            return;
        }
        CustomMob killerMob = this.service.find(killer.getUniqueId()).orElse(null);
        if (killerMob == null || !this.killCounts(killerMob, dead)) {
            return;
        }
        this.skills.fire(killerMob, SkillEvent.ON_KILL, dead);
    }

    /** {@code true} si la muerte encaja con {@code trigger.kill} de alguna skill del mob. */
    private boolean killCounts(CustomMob killer, LivingEntity dead) {
        boolean player = dead instanceof Player;
        for (SkillSpec skill : killer.definition().skills()) {
            if (!skill.hasEvent(SkillEvent.ON_KILL)) {
                continue;
            }
            switch (skill.kill()) {
                case ANY -> {
                    return true;
                }
                case PLAYERS -> {
                    if (player) {
                        return true;
                    }
                }
                case MOBS -> {
                    if (!player) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * Autor del golpe: un jugador, o un mob custom. Tanto cuerpo a cuerpo como por
     * proyectil, en cuyo caso manda quien lo disparo.
     */
    private LivingEntity attackerOf(EntityDamageByEntityEvent event) {
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile) {
            source = projectile.getShooter() instanceof Entity shooter ? shooter : null;
        }
        if (!(source instanceof LivingEntity living)) {
            return null;
        }
        if (living instanceof Player player) {
            return player;
        }
        return this.service.find(living.getUniqueId()).isPresent() ? living : null;
    }
}
