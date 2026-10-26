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
 * <p>No compara dos muestras seguidas: un mob de IA da un paso y se queda parado casi un
 * segundo, asi que la diferencia entre dos ciclos es cero la mayor parte del tiempo y la
 * marcha no se detectaba nunca. En su lugar se compara contra la posicion de hace una
 * <b>ventana</b> de muestras: si en ese tiempo se ha movido de verdad, anda.
 *
 * <p>Tampoco toca a los mobs que estan en una animacion puntual (golpe, dano, muerte).
 */
public final class ModelStateTask extends BukkitRunnable {

    /**
     * Distancia (al cuadrado) que cuenta como desplazamiento dentro de la ventana.
     *
     * <p>0,25 bloques en un segundo: por debajo de eso es ruido de posicion, no un paso.
     */
    private static final double MOVING_EPSILON = 0.0625D;

    /** Muestras que abarca la ventana: 4 x 5 ticks = 20 ticks (un segundo). */
    private static final int WINDOW = 4;

    /**
     * Ticks durante los que esta tarea no toca un mob que acaba de golpear o recibir dano.
     *
     * <p>Mas que un ciclo de la tarea (5 t): el evento puede llegar justo despues de una muestra.
     */
    private static final long REACTIVE_GRACE_TICKS = 3L;

    private final MobService service;
    /** Ultimas posiciones por mob, de la mas nueva a la mas vieja. */
    private final Map<UUID, Vector[]> history = new ConcurrentHashMap<>();
    /** Estado que la tarea cree que tiene puesto cada mob; {@code null} = sin evaluar. */
    private final Map<UUID, ModelAnimation> shown = new ConcurrentHashMap<>();

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
            if (customMob.definition().model().animation(ModelAnimation.WALK) == null
                    && customMob.definition().model().animation(ModelAnimation.IDLE) == null) {
                // Sin animaciones de estado no hay nada que alternar.
                continue;
            }
            UUID id = moved.getUniqueId();
            alive.add(id);
            Vector now = moved.getLocation().toVector();

            // Una animacion puntual (golpe, dano, muerte) manda: se reinicia la ventana para no
            // contar como marcha el rato que no se le ha mirado.
            if (link.isBusy(entity) || link.reactedRecently(entity, REACTIVE_GRACE_TICKS)) {
                // Golpe, dano o muerte en curso: el estado se pausa. Se olvida lo que habia
                // puesto para reevaluarlo al terminar (si sigue andando, vuelve walk).
                //
                // 'reactedRecently' cubre el mismo tick: el mob se para a atacar, esta tarea pide
                // idle y el golpe llega acto seguido. Sin la gracia, idle le ganaba la partida.
                this.history.remove(id);
                this.shown.remove(id);
                continue;
            }

            Vector[] window = this.push(id, now);
            if (window[WINDOW - 1] == null) {
                // La ventana aun no esta llena desde que aparecio o dejo de estar ocupado.
                continue;
            }
            boolean moving = window[WINDOW - 1].distanceSquared(now) > MOVING_EPSILON;
            ModelAnimation next = moving ? ModelAnimation.WALK : ModelAnimation.IDLE;
            ModelAnimation current = this.shown.get(id);
            if (current == next) {
                continue;
            }
            // Solo se pide si el modelo declara esa animacion; el enlace ignora el resto.
            this.shown.put(id, next);
            link.animate(entity, customMob.definition(), next);
        }
        this.history.keySet().retainAll(alive);
        this.shown.keySet().retainAll(alive);
    }

    /** Mete la posicion nueva y devuelve la ventana, con la mas nueva en el indice 0. */
    private Vector[] push(UUID id, Vector position) {
        Vector[] previous = this.history.get(id);
        Vector[] window = new Vector[WINDOW];
        window[0] = position;
        for (int i = 1; i < WINDOW; i++) {
            window[i] = previous == null ? null : previous[i - 1];
        }
        this.history.put(id, window);
        return window;
    }

    @Override
    public synchronized void cancel() throws IllegalStateException {
        super.cancel();
        this.history.clear();
        this.shown.clear();
    }
}
