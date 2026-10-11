package com.juanp.custommobs.mob;

import org.bukkit.entity.LivingEntity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityTransformEvent;

/**
 * Deja a un mob custom ser —o no ser— transformado por el juego.
 *
 * <p>El juego transforma mobs todo el tiempo: un aldeano mordido se vuelve zombi aldeano, a uno
 * le cae un rayo y sale bruja, un zombi que se ahoga pasa a ahogado, un cerdo con un rayo se
 * vuelve piglin zombificado... Para un mob custom eso es perder la definicion: dejaria de ser el
 * mob que el servidor definio.
 *
 * <p>Con {@code transformable: false} en su yml, ninguna de esas conversiones le pasa. Por
 * defecto se transforma como cualquier otro mob, que es como se ha comportado siempre.
 */
public final class TransformBlockListener implements Listener {

    private final MobService service;

    public TransformBlockListener(MobService service) {
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true)
    public void onTransform(EntityTransformEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) {
            return;
        }
        CustomMob customMob = this.service.find(living.getUniqueId()).orElse(null);
        if (customMob == null || customMob.definition().transformable()) {
            return;
        }
        event.setCancelled(true);
    }
}
