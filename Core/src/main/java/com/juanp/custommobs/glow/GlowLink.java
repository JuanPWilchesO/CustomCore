package com.juanp.custommobs.glow;

import com.juanp.custommobs.CustomMobsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

/**
 * El brillo de facciones por espectador, con o sin PacketEvents.
 *
 * <p>PacketEvents es una dependencia opcional: si falta, el plugin debe arrancar igual y
 * limitarse a no pintar. Como {@link FactionGlowService} usa sus clases directamente, solo
 * se puede cargar cuando esta instalado; por eso lo demas habla con esta interfaz y hay una
 * version vacia ({@link NoGlowLink}) para cuando no lo esta.
 *
 * <p>Es el mismo patron que ya usan Teams, LuckPerms y BetterModel.
 */
public interface GlowLink {

    /** Arranca la tarea periodica; sin PacketEvents no hace nada. */
    void start(CustomMobsPlugin plugin);

    /** Detiene la tarea, si estaba arrancada. */
    void stop();

    /** {@code true} si el brillo por espectador funciona de verdad. */
    boolean available();

    /** Alterna el brillo de un jugador. Devuelve como queda: {@code true} si activado. */
    boolean toggle(Player player);

    /** {@code true} si ese jugador ve el brillo de facciones. */
    boolean enabledFor(Player player);

    /** Modo configurado: {@code off}, {@code global} o {@code player}. */
    String mode();
}
