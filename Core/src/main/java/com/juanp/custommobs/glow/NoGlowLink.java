package com.juanp.custommobs.glow;

import com.juanp.custommobs.CustomMobsPlugin;
import org.bukkit.entity.Player;

/**
 * Brillos sin PacketEvents: no pinta nada y no revienta.
 *
 * <p>Sin PacketEvents no se puede mandar un equipo falso al cliente de cada jugador, que es
 * lo unico que da el color por espectador. El brillo queda apagado, que es lo mismo que con
 * {@code faction-glow.mode: off}, y el resto del plugin funciona igual.
 */
public final class NoGlowLink implements GlowLink {

    @Override
    public void start(CustomMobsPlugin plugin) {
        // Nada que arrancar.
    }

    @Override
    public void stop() {
        // Nada que parar.
    }

    @Override
    public boolean available() {
        return false;
    }

    @Override
    public boolean toggle(Player player) {
        // Sin PacketEvents no hay nada que alternar: se devuelve como queda, apagado.
        return false;
    }

    @Override
    public boolean enabledFor(Player player) {
        return false;
    }

    @Override
    public String mode() {
        return "off";
    }
}
