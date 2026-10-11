package com.juanp.custommobs.combat;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowman;
import org.bukkit.entity.Wolf;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetLivingEntityEvent;

/**
 * El mob amistoso con el pueblo se porta como un golem, pero mas radical.
 *
 * <ol>
 *   <li><b>No lo molestan los golems.</b> El golem ataca a cualquier {@code Monster} que tenga
 *       cerca, da igual el disfraz o lo que diga su definicion. Con
 *       {@code village-friendly: true}, cuando un golem de hierro o de nieve lo apunta, se
 *       cancela la eleccion de objetivo.</li>
 *   <li><b>Defiende a los aldeanos.</b> Si un jugador le pega a un aldeano cerca, el mob se lo
 *       apunta como agresion, igual que un golem sale a por quien maltrata al pueblo. Y aqui
 *       es mas radical: <b>cualquier</b> mob amistoso que lo vea reacciona, sea cual sea su
 *       actitud —hasta un neutral o un defensor—, porque la agresion pesa mas que la actitud.</li>
 * </ol>
 *
 * <p>Ojo con la otra mitad, que aqui <b>no</b> se puede decidir: que los aldeanos
 * <b>no huyan</b> depende del tipo de entidad. Huyen de zombis e illagers, no de esqueletos.
 * Para un mob que conviva con aldeanos, usa la familia <b>esqueleto</b> y, si quieres otra
 * apariencia, disfrazalo: el sistema de disfraces admite cualquier tipo de mob.
 */
public final class VillagePeaceListener implements Listener {

    private final MobService service;

    public VillagePeaceListener(MobService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetLivingEntityEvent event) {
        LivingEntity target = event.getTarget();
        if (target == null) {
            return;
        }
        CustomMob mob = this.service.find(target.getUniqueId()).orElse(null);
        if (mob == null || !mob.definition().villageFriendly()) {
            return;
        }
        Entity source = event.getEntity();
        // Golems y munecos de nieve van a por los monstruos, y el lobo va a por los
        // esqueletos: contra un mob pacifico del pueblo, ninguno de los tres tiene nada
        // que hacer.
        if (source instanceof IronGolem || source instanceof Snowman || source instanceof Wolf) {
            event.setCancelled(true);
            event.setTarget(null);
        }
    }

    /**
     * Le pegaron a un aldeano: los mobs amistosos que lo vean se apuntan al agresor.
     *
     * <p>Se usa el mismo registro de agresion que la venganza normal, asi que la IA y las
     * skills reaccionan igual: la hostilidad dura lo que diga {@code aggro-duration-seconds} y
     * se percibe dentro de {@code aggro-watch-radius}.
     */
    @EventHandler(ignoreCancelled = true)
    public void onVillagerHurt(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof AbstractVillager villager)) {
            return;
        }
        Player attacker = this.attackerOf(event);
        if (attacker == null) {
            return;
        }
        double radius = this.service.config().aggroWatchRadius();
        long duration = this.service.config().aggroDurationMillis();
        for (Entity near : villager.getNearbyEntities(radius, radius, radius)) {
            CustomMob mob = this.service.find(near.getUniqueId()).orElse(null);
            if (mob == null || !mob.definition().villageFriendly()) {
                continue;
            }
            this.service.aggro().mark(mob.entity().getUniqueId(), attacker.getUniqueId(), duration);
        }
    }

    /** Quien ha pegado: el jugador, o el que disparo el proyectil. */
    private Player attackerOf(EntityDamageByEntityEvent event) {
        if (event.getDamager() instanceof Player player) {
            return player;
        }
        if (event.getDamager() instanceof Projectile projectile
                && projectile.getShooter() instanceof Player shooter) {
            return shooter;
        }
        return null;
    }
}
