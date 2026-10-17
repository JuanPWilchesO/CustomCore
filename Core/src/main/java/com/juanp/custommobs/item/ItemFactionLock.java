package com.juanp.custommobs.item;

import com.juanp.custommobs.drop.ItemCatalog;
import com.juanp.custommobs.mob.MobService;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Restricciones de un objeto del catalogo: <b>faccion</b> y/o <b>clase</b>.
 *
 * <p>Si su yml declara {@code faccion:} y {@code clase:}, el objeto solo lo usa quien cumpla
 * <b>las dos</b>. La marca viaja dentro del propio objeto (su NBT), asi que sobrevive a que lo
 * suelten, lo guarden en un cofre o lo reparta otro plugin.
 *
 * <p>No es una caja fuerte: impide usarlo, no quitarselo a quien ya lo tenga.
 */
public final class ItemFactionLock implements Listener {

    /** Cuanto se calla el aviso, por jugador, para no inundar el chat a clics. */
    private static final long NOTICE_COOLDOWN_MS = 3000L;

    private final MobService service;
    private final ItemCatalog catalog;
    private final Map<UUID, Long> lastNotice = new ConcurrentHashMap<>();

    public ItemFactionLock(MobService service, ItemCatalog catalog) {
        this.service = service;
        this.catalog = catalog;
    }

    private static String tag(ItemStack item, org.bukkit.NamespacedKey key) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        return meta == null ? null
                : meta.getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    /**
     * El motivo por el que ese jugador no puede usar el objeto, o {@code null} si puede.
     *
     * <p>Un admin pasa siempre: reservar un objeto no puede dejarle sin poder revisarlo.
     */
    public String requirement(Player player, ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        // El bypass es su propio permiso: se puede quitar para probar sin perder
        // el acceso a los comandos de administracion.
        if (player.hasPermission(this.service.config().bypassPermission())) {
            return null;
        }
        String faction = tag(item, this.catalog.factionKey());
        if (faction != null) {
            String own = this.service.factionOf(player);
            boolean allowed = own != null && own.equalsIgnoreCase(faction);
            // Si el servidor lo permite, un aliado tambien vale. Por defecto, no: la
            // restriccion es de la faccion exacta y un aliado no hereda el objeto.
            if (!allowed && own != null && this.service.config().itemFactionAllies()) {
                allowed = this.service.factions().isAlly(
                        faction.toLowerCase(Locale.ROOT), own.toLowerCase(Locale.ROOT));
            }
            if (!allowed) {
                return "Ese objeto es de la faccion " + faction + ": no es la tuya.";
            }
        }
        String clazz = tag(item, this.catalog.classKey());
        if (clazz != null) {
            String own = this.service.plugin().playerClasses().of(player.getUniqueId()).orElse(null);
            if (own == null || !own.equalsIgnoreCase(clazz)) {
                return "Ese objeto es de la clase " + clazz + ": no es la tuya.";
            }
        }
        return null;
    }

    /** Avisa con enfriamiento: a clics seguidos no inunda el chat. */
    private void deny(Player player, String reason) {
        long now = System.currentTimeMillis();
        Long previous = this.lastNotice.get(player.getUniqueId());
        if (previous != null && now - previous < NOTICE_COOLDOWN_MS) {
            return;
        }
        this.lastNotice.put(player.getUniqueId(), now);
        player.sendMessage(Texts.color("&c" + reason));
    }

    /** Lo suelta en el suelo: el objeto no es suyo, asi que no le ocupa el inventario. */
    private void drop(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        player.getWorld().dropItemNaturally(player.getLocation(), item);
    }

    /** No puede craftearlo. */
    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !this.service.itemLockActive(player.getWorld())) {
            return;
        }
        String reason = this.requirement(player, event.getCurrentItem());
        if (reason == null) {
            return;
        }
        event.setCancelled(true);
        this.deny(player, reason);
    }

    /** No puede recogerlo del suelo. */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !this.service.itemLockActive(player.getWorld())) {
            return;
        }
        String reason = this.requirement(player, event.getItem().getItemStack());
        if (reason == null) {
            return;
        }
        event.setCancelled(true);
        this.deny(player, reason);
    }

    /** No puede usarlo ni equiparlo con el clic derecho de la mano. */
    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        // Las dos manos: si no vale para la principal, tampoco para la secundaria.
        if (!this.service.itemLockActive(event.getPlayer().getWorld())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String reason = this.requirement(event.getPlayer(), event.getItem());
        if (reason == null) {
            return;
        }
        event.setCancelled(true);
        this.deny(event.getPlayer(), reason);
    }

    /**
     * No lo puede mover ni sacarlo de un cofre: si no es suyo, no lo toca.
     *
     * <p>Se mira lo que hay bajo el cursor y en la ranura; con eso quedan cubiertos el
     * inventario propio, los cofres y las ranuras de armadura de una sola vez.
     */
    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !this.service.itemLockActive(player.getWorld())) {
            return;
        }
        ItemStack cursor = event.getCursor();
        String reason = this.requirement(player, cursor);
        if (reason != null) {
            event.setCancelled(true);
            player.setItemOnCursor(null);
            this.drop(player, cursor);
            this.deny(player, reason);
            return;
        }
        ItemStack current = event.getCurrentItem();
        reason = this.requirement(player, current);
        if (reason == null) {
            return;
        }
        event.setCancelled(true);
        // Solo se suelta si esta en SU inventario. En un cofre ajeno se cancela y ya: el
        // objeto se queda donde estaba, que no es suyo para andar tirandolo.
        boolean ownInventory = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof Player;
        if (ownInventory) {
            event.setCurrentItem(null);
            this.drop(player, current);
        }
        this.deny(player, reason);
    }

    /** Tampoco puede pegar con el objeto restringido: atacar tambien es usarlo. */
    @EventHandler(ignoreCancelled = true)
    public void onAttack(EntityDamageByEntityEvent event) {
        // Solo cuerpo a cuerpo: si el dano viene de una flecha, ya se bloqueo el disparo.
        if (!(event.getDamager() instanceof Player player)
                || !this.service.itemLockActive(player.getWorld())) {
            return;
        }
        String reason = this.requirement(player, player.getInventory().getItemInMainHand());
        if (reason == null) {
            return;
        }
        event.setCancelled(true);
        this.deny(player, reason);
    }

    /** Ni disparar con un arco o ballesta restringidos. */
    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !this.service.itemLockActive(player.getWorld())) {
            return;
        }
        String reason = this.requirement(player, event.getBow());
        if (reason == null) {
            return;
        }
        event.setCancelled(true);
        this.deny(player, reason);
    }

    /** Ni lanzar una tridente restringida. */
    @EventHandler(ignoreCancelled = true)
    public void onLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof org.bukkit.entity.Trident)
                || !(event.getEntity().getShooter() instanceof Player player)
                || !this.service.itemLockActive(player.getWorld())) {
            return;
        }
        String reason = this.requirement(player, player.getInventory().getItemInMainHand());
        if (reason == null) {
            reason = this.requirement(player, player.getInventory().getItemInOffHand());
        }
        if (reason == null) {
            return;
        }
        event.setCancelled(true);
        this.deny(player, reason);
    }

    /**
     * Ni pasarlo a la mano secundaria con la tecla de intercambio: seria moverlo, y ademas
     * dejaria el objeto accesible desde una mano que el clic derecho tambien usa.
     */
    @EventHandler(ignoreCancelled = true)
    public void onSwapHands(org.bukkit.event.player.PlayerSwapHandItemsEvent event) {
        Player player = event.getPlayer();
        if (!this.service.itemLockActive(player.getWorld())) {
            return;
        }
        String reason = this.requirement(player, event.getMainHandItem());
        if (reason == null) {
            reason = this.requirement(player, event.getOffHandItem());
        }
        if (reason == null) {
            return;
        }
        event.setCancelled(true);
        this.deny(player, reason);
    }

    /** Tampoco puede arrastrarlo por encima de varias ranuras. */
    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !this.service.itemLockActive(player.getWorld())) {
            return;
        }
        for (ItemStack stack : event.getNewItems().values()) {
            String reason = this.requirement(player, stack);
            if (reason != null) {
                event.setCancelled(true);
                this.deny(player, reason);
                return;
            }
        }
    }
}
