package com.juanp.custommobs.model;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
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
}
