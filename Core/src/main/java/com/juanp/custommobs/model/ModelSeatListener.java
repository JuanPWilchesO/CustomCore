package com.juanp.custommobs.model;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDismountEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;

/**
 * Sube al jugador al asiento del modelo en las monturas que lo piden.
 *
 * <p>Un modelo con un hueso marcado como {@code seat} no admite pasajero normal: la entidad
 * vanilla ya no es visible y el asiento es un hitbox de BetterModel. Por eso, con
 * {@code model.seat: true}, el clic derecho sube al jugador al hueso en vez de montarlo en
 * el caballo.
 */
public final class ModelSeatListener implements Listener {

    private final MobService service;

    public ModelSeatListener(MobService service) {
        this.service = service;
    }

    /**
     * El jugador monta la montura vanilla.
     *
     * <p>La montura se monta a la manera de siempre (el asiento del modelo se aparto porque
     * dejaba el caballo sin movimiento), asi que aqui solo hay que evitar que el jinete salga
     * sentado en el aire: con jinete, el caballo deja de ocultarse.
     */
    @EventHandler
    public void onMount(org.bukkit.event.vehicle.VehicleEnterEvent event) {
        if (!(event.getEntered() instanceof Player player)
                || !(event.getVehicle() instanceof LivingEntity mount)) {
            return;
        }
        CustomMob customMob = this.service.find(mount.getUniqueId()).orElse(null);
        if (customMob == null || !customMob.definition().mountable()) {
            return;
        }
        if (this.service.modelLink() instanceof BetterModelLink better) {
            better.riderMounted(mount, true);
        }
    }

    /** Al bajar, el caballo vuelve a ocultarse (solo se ve el modelo). */
    @EventHandler
    public void onLeave(org.bukkit.event.vehicle.VehicleExitEvent event) {
        if (!(event.getExited() instanceof Player)
                || !(event.getVehicle() instanceof LivingEntity mount)) {
            return;
        }
        CustomMob customMob = this.service.find(mount.getUniqueId()).orElse(null);
        if (customMob == null || !customMob.definition().mountable()) {
            return;
        }
        if (this.service.modelLink() instanceof BetterModelLink better) {
            better.riderMounted(mount, false);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Entity clicked = event.getRightClicked();
        if (!(clicked instanceof LivingEntity target)) {
            return;
        }
        Player player = event.getPlayer();
        if (player.isInsideVehicle() || player.isSneaking()) {
            // Agachado se sigue pudiendo hacer otras cosas con el mob.
            return;
        }
        CustomMob customMob = this.service.find(target.getUniqueId()).orElse(null);
        if (customMob == null || !customMob.definition().mountable()) {
            return;
        }
        ModelLink link = this.service.modelLink();
        if (!link.seatEnabled(target, customMob.definition())) {
            return;
        }
        link.seat(target, customMob.definition(), player);
    }

    /**
     * El jugador se ha bajado: se devuelve el controlador del asiento a {@code NONE}.
     *
     * <p>El asiento es el hitbox del modelo, asi que quien se desmonta es el pasajero de ESA
     * entidad, no del caballo: por eso se mira la entidad que se abandona y no la montura.
     */
    @EventHandler
    public void onDismount(EntityDismountEvent event) {
        if (!(event.getDismounted() instanceof LivingEntity seat)) {
            return;
        }
        CustomMob customMob = this.service.find(seat.getUniqueId()).orElse(null);
        if (customMob == null || !customMob.definition().mountable()) {
            return;
        }
        if (event.getEntity() instanceof Player rider) {
            this.service.modelLink().unseat(seat, customMob.definition(), rider);
        }
    }
}
