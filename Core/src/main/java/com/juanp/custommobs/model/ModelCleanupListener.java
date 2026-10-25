package com.juanp.custommobs.model;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.entity.Entity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityRemoveEvent;

/**
 * Suelta el modelo de un mob que desaparece del mundo sin haber muerto.
 *
 * <p>Un tracker abierto deja el modelo flotando en el aire: su entidad ya no existe, asi
 * que no hay nadie que lo actualice. La muerte tiene su propio camino (la animacion de
 * despedida); aqui se cubre el resto: recogido en el huevo, quitado con
 * {@code /customcore remove}, retirado por la ventana de abandono o por un portal.
 */
public final class ModelCleanupListener implements Listener {

    private final MobService service;

    public ModelCleanupListener(MobService service) {
        this.service = service;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRemove(EntityRemoveEvent event) {
        Entity entity = event.getEntity();
        if (!(entity instanceof org.bukkit.entity.LivingEntity living)) {
            return;
        }
        CustomMob customMob = this.service.find(living.getUniqueId()).orElse(null);
        if (customMob == null) {
            return;
        }
        // Al morir no se suelta aqui: la animacion de despedida necesita el modelo vivo.
        if (event.getCause() == EntityRemoveEvent.Cause.DEATH) {
            return;
        }
        this.service.modelLink().release(living);
    }
}
