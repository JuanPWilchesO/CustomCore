package com.juanp.custommobs.cc;

import com.juanp.custommobs.drop.ItemCatalog;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;

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
    private final ItemCatalog catalog;

    public CcListener(CcService cc, ItemCatalog catalog) {
        this.cc = cc;
        this.catalog = catalog;
    }

    /** Objetos marcados con 'stun-free: true' se pueden usar bajo control. */
    private boolean free(ItemStack item) {
        return this.catalog != null && this.catalog.stunFree(item);
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

    /** El dano recibido puede romper un control marcado con 'cancel-on-hit'. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamageTaken(EntityDamageEvent event) {
        if (event.getEntity() instanceof LivingEntity living) {
            this.cc.onDamage(living);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent event) {
        var id = event.getPlayer().getUniqueId();
        if ((this.cc.stunned(id) || this.cc.silenced(id)) && !this.free(event.getItem())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        var id = event.getPlayer().getUniqueId();
        ItemStack item = event.getPlayer().getInventory().getItem(event.getHand());
        if ((this.cc.stunned(id) || this.cc.silenced(id)) && !this.free(item)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onConsume(PlayerItemConsumeEvent event) {
        var id = event.getPlayer().getUniqueId();
        if ((this.cc.stunned(id) || this.cc.silenced(id)) && !this.free(event.getItem())) {
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

    /** Si el servidor se cerro con un bloqueo a medias, se repara al entrar. */
    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        this.cc.repair(event.getPlayer());
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
