package com.juanp.custommobs.cc;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Lo que cada control bloquea:
 *
 * <ul>
 *   <li><b>stun</b>: mover, atacar, items y disparar.</li>
 *   <li><b>root</b>: solo mover (los atributos lo hacen; aqui no se cancela nada).</li>
 *   <li><b>silence</b>: items y disparar; puede moverse y pelear cuerpo a cuerpo.</li>
 *   <li><b>disarm</b>: atacar y disparar; puede moverse y usar items.</li>
 * </ul>
 */
public final class CcListener implements Listener {

    private final CcService cc;

    public CcListener(CcService cc) {
        this.cc = cc;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) {
            return;
        }
        var id = player.getUniqueId();
        if (this.cc.stunned(id) || this.cc.disarmed(id)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        var id = event.getPlayer().getUniqueId();
        if (this.cc.stunned(id) || this.cc.silenced(id)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        var id = event.getPlayer().getUniqueId();
        if (this.cc.stunned(id) || this.cc.silenced(id)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        var id = event.getPlayer().getUniqueId();
        if (this.cc.stunned(id) || this.cc.silenced(id)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        var id = player.getUniqueId();
        if (this.cc.stunned(id) || this.cc.silenced(id) || this.cc.disarmed(id)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        this.cc.release(event.getPlayer());
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent event) {
        this.cc.release(event.getEntity());
    }
}
