package com.juanp.custommobs.item;

import com.juanp.custommobs.drop.ItemCatalog;
import com.juanp.custommobs.mob.MobService;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Exclusividad de faccion de un objeto del catalogo.
 *
 * <p>Un objeto guardado con {@code /customcore item faccion <nombre> <faccion>} queda
 * reservado: solo un jugador de esa faccion puede <b>recogerlo</b>, <b>equiparlo</b> o
 * <b>usarlo</b>. La marca viaja dentro del propio objeto (su NBT), asi que sobrevive a que
 * lo suelten, lo guarden en un cofre o lo reparta otro plugin.
 *
 * <p>No es una caja fuerte: el objeto sigue siendo un objeto normal. Lo que se impide es
 * que alguien de otra faccion lo use; no se le quita de las manos a quien ya lo tenga.
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

    /** La faccion a la que pertenece el objeto, o {@code null} si es de todos. */
    private String factionOf(ItemStack item) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return null;
        }
        return meta.getPersistentDataContainer().get(this.catalog.factionKey(), PersistentDataType.STRING);
    }

    /** {@code true} si el jugador es de esa faccion (o el objeto es libre). */
    private boolean mayUse(Player player, String faction) {
        if (faction == null) {
            return true;
        }
        // Un admin pasa siempre: reservar un objeto no puede dejarle sin poder moverlo ni
        // revisarlo. Un op tiene todos los permisos, asi que cae aqui solo.
        if (player.hasPermission(this.service.config().adminPermission())) {
            return true;
        }
        String own = this.service.factionOf(player);
        return own != null && own.equalsIgnoreCase(faction);
    }

    private void deny(Player player, String faction) {
        long now = System.currentTimeMillis();
        Long previous = this.lastNotice.get(player.getUniqueId());
        if (previous != null && now - previous < NOTICE_COOLDOWN_MS) {
            return;
        }
        this.lastNotice.put(player.getUniqueId(), now);
        player.sendMessage(Texts.color("&cEse objeto es de la faccion &f" + faction
                + "&c: no es la tuya."));
    }

    /**
     * Un objeto reservado solo lo <b>craftea</b> quien es de esa faccion.
     *
     * <p>La receta puede salir en el libro de recetas de cualquiera —eso no se puede
     * esconder por jugador—, pero el crafteo no se completa.
     */
    @EventHandler(ignoreCancelled = true)
    public void onCraft(CraftItemEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !this.service.itemLockActive(player.getWorld())) {
            return;
        }
        String faction = this.factionOf(event.getCurrentItem());
        if (faction == null || this.mayUse(player, faction)) {
            return;
        }
        event.setCancelled(true);
        this.deny(player, faction);
    }

    /** Lo suelta en el suelo: el objeto no es suyo, asi que no le ocupa el inventario. */
    private void drop(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return;
        }
        player.getWorld().dropItemNaturally(player.getLocation(), item);
    }

    /** No puede recogerlo del suelo. */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)
                || !this.service.itemLockActive(player.getWorld())) {
            return;
        }
        String faction = this.factionOf(event.getItem().getItemStack());
        if (faction == null || this.mayUse(player, faction)) {
            return;
        }
        event.setCancelled(true);
        this.deny(player, faction);
    }

    /** No puede usarlo ni equiparlo con el clic derecho de la mano. */
    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND
                || !this.service.itemLockActive(event.getPlayer().getWorld())) {
            return;
        }
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        String faction = this.factionOf(event.getItem());
        if (faction == null || this.mayUse(event.getPlayer(), faction)) {
            return;
        }
        event.setCancelled(true);
        this.deny(event.getPlayer(), faction);
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
        // Lo que trae el cursor manda: si es de otra faccion, se cae al suelo.
        ItemStack cursor = event.getCursor();
        String faction = this.lockedFor(player, cursor);
        if (faction != null) {
            event.setCancelled(true);
            player.setItemOnCursor(null);
            this.drop(player, cursor);
            this.deny(player, faction);
            return;
        }
        ItemStack current = event.getCurrentItem();
        faction = this.lockedFor(player, current);
        if (faction == null) {
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
        this.deny(player, faction);
    }

    /** Tampoco puede arrastrarlo por encima de varias ranuras. */
    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)
                || !this.service.itemLockActive(player.getWorld())) {
            return;
        }
        for (ItemStack stack : event.getNewItems().values()) {
            String faction = this.lockedFor(player, stack);
            if (faction != null) {
                event.setCancelled(true);
                this.deny(player, faction);
                return;
            }
        }
    }

    /** La faccion del objeto si el jugador NO puede usarlo; {@code null} si puede o es libre. */
    private String lockedFor(Player player, ItemStack... items) {
        for (ItemStack item : items) {
            String faction = this.factionOf(item);
            if (faction != null && !this.mayUse(player, faction)) {
                return faction;
            }
        }
        return null;
    }
}
