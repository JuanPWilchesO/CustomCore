package com.juanp.custommobs.model;

import com.juanp.custommobs.mob.CustomMob;
import com.juanp.custommobs.mob.MobService;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Cambia la animacion del modelo entre "andando" y "en reposo".
 *
 * <p>Corre cada 5 ticks: mas a menudo es gasto inutil (el cliente no distingue el matiz) y
 * menos se nota el arranque de la marcha. Solo mira mobs que tengan modelo y las dos
 * animaciones; el resto no cuesta nada.
 */
public final class ModelStateTask extends BukkitRunnable {

    /** Varianza admitida en la posicion para considerar que el mob esta quieto. */
    private static final double MOVING_EPSILON = 0.0025D;

    private final MobService service;
    private final Map<UUID, Vector> last = new ConcurrentHashMap<>();

    public ModelStateTask(MobService service) {
        this.service = service;
    }

    @Override
    public void run() {
        ModelLink link = this.service.modelLink();
        Set<UUID> alive = new HashSet<>();
        for (CustomMob customMob : this.service.active()) {
            LivingEntity entity = customMob.entity();
            if (!(entity instanceof Entity moved) || !moved.isValid()) {
                continue;
            }
            if (customMob.definition().model() == null || !customMob.definition().model().active()) {
                continue;
            }
            if (customMob.definition().model().animation(ModelAnimation.WALK) == null) {
                // Sin animacion de marcha no hay nada que alternar.
                continue;
            }
            UUID id = moved.getUniqueId();
            alive.add(id);
            Vector previous = this.last.put(id, moved.getLocation().toVector());
            if (previous == null) {
                // Primera muestra: sin referencia no se sabe si anda.
                continue;
            }
            boolean moving = previous.distanceSquared(moved.getLocation().toVector())
                    > MOVING_EPSILON;
            if (moving) {
                link.animate(entity, customMob.definition(), ModelAnimation.WALK);
            } else {
                // El enlace solo repite el reposo si el mob venia andando.
                link.stopWalking(entity, customMob.definition());
            }
        }
        this.last.keySet().retainAll(alive);
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        super.cancel();
        this.last.clear();
    }
}
