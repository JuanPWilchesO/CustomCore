package com.juanp.custommobs.glow;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Limpia el estado del brillo al salir.
 *
 * <p>Los equipos falsos de PacketEvents son por conexion: el cliente nuevo no recuerda ninguno.
 * Si al reconectar el servicio sigue creyendo que el equipo ya existe ({@code created}), no lo
 * vuelve a crear y el color y la placa no reaparecen. Por eso se olvida todo al salir.
 *
 * <p>Tambien evita una fuga lenta: los mapas de espectadores crecian con cada jugador que
 * alguna vez estuvo conectado.
 */
public final class GlowListener implements Listener {

    private final FactionGlowService glow;

    public GlowListener(FactionGlowService glow) {
        this.glow = glow;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.glow.forgetPlayer(event.getPlayer());
    }
}
