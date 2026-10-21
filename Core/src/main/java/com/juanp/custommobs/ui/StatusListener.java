package com.juanp.custommobs.ui;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;

/** Limpia las barras al salir para no dejar boss bars colgando. */
public final class StatusListener implements Listener {

    private final StatusService status;

    public StatusListener(StatusService status) {
        this.status = status;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.status.clearAll(event.getPlayer());
    }
}
