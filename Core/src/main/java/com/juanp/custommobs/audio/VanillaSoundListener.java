package com.juanp.custommobs.audio;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;

/**
 * Quita el sonido vanilla que no tiene su propio tipo de sonido en el yml.
 *
 * <p>La regla del proyecto: si el mob configura un tipo de sonido, ese tipo deja de sonar a
 * vanilla (si no, se oyen las dos cosas a la vez); los tipos que no configura siguen con el
 * sonido de siempre. Tipos que existen: {@code ambient}, {@code hurt}, {@code death} y
 * {@code attack}.
 *
 * <p>Vanilla no tiene un evento de sonido por tipo, solo {@code EntitySoundEvent}, asi que la
 * traduccion es por evento:
 * <ul>
 *   <li>{@code attack}: el mob golpea. Se cancela el evento <b>antes</b> de que vanila lo
 *       procese (prioridad {@link EventPriority#LOWEST}): ahi es donde suena. Esa cancelacion
 *       solo silencia el sonido y la particula de golpe; el dano lo aplica el evento original
 *       (el ataque cuerpo a cuerpo), y cualquier plugin que lo recalcule despues lo vuelve a
 *       hacer. El listener de animaciones y efectos es independiente y no se entera.</li>
 *   <li>{@code hurt}: el mob recibe dano. Se cancela igual, ignorando el dano por fuego, que
 *       suena con el sonido de fuego y no con el de dano.</li>
 *   <li>{@code death}: no se puede silenciar por evento sin romper la muerte, asi que el
 *       sonido configurado <b>se suma</b> al de vanilla en vez de sustituirlo. La animacion
 *       de muerte (si el mob tiene modelo) es lo que tapa el cuerpo; el sonido no.</li>
 *   <li>{@code ambient}: vanilla usa el mismo sonido para el ambiente y para el "mira aqui",
 *       y no hay evento que los separe. El configurado se oye igualmente en su intervalo,
 *       mezclado con el vanilla.</li>
 * </ul>
 */
public final class VanillaSoundListener implements Listener {

    private final MobService service;

    public VanillaSoundListener(MobService service) {
        this.service = service;
    }

    /** El mob golpea: vanilla suena justo despues de este evento, asi que se cancela. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobAttack(EntityDamageByEntityEvent event) {
        Entity source = event.getDamager();
        if (source instanceof Projectile projectile) {
            source = projectile.getShooter() instanceof Entity shooter ? shooter : null;
        }
        if (!(source instanceof org.bukkit.entity.LivingEntity attacker)) {
            return;
        }
        CustomMob customMob = this.service.find(attacker.getUniqueId()).orElse(null);
        if (customMob == null || customMob.definition().sounds().attack() == null) {
            return;
        }
        // Cancela el sonido y la particula de golpe vanilla; el dano lo aplica el ataque que
        // genero el evento (o el plugin que lo recalcule), no este evento por si solo.
        event.setCancelled(true);
    }

    /** El mob recibe dano: se calla el de vanila si el yml trae el suyo de dano. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onMobHurt(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.LivingEntity victim)) {
            return;
        }
        CustomMob customMob = this.service.find(victim.getUniqueId()).orElse(null);
        if (customMob == null || customMob.definition().sounds().hurt() == null) {
            return;
        }
        // El fuego suena con su propio sonido, no con el de dano: no se toca.
        switch (event.getCause()) {
            case FIRE, FIRE_TICK, LAVA:
                return;
            default:
                event.setCancelled(true);
        }
    }

    /** El mob muere: no se puede callar la muerte, pero al menos se avisa en el log. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onMobDeath(EntityDeathEvent event) {
        this.service.find(event.getEntity().getUniqueId()).ifPresent(customMob -> {
            if (customMob.definition().sounds().death() == null) {
                return;
            }
            // SoundListener ya reproduce el configurado. Aqui solo se deja constancia de que el
            // de vanilla tambien suena, para que no parezca un fallo al probarlo.
            if (this.service.config().debug()) {
                this.service.plugin().getLogger().info("[sonido] muerte de '"
                        + customMob.definition().id() + "': suena el configurado y tambien el"
                        + " de vanilla (Bukkit no deja callar la muerte).");
            }
        });
    }
}
