package com.juanp.custommobs.item;

import com.juanp.custommobs.drop.ItemCatalog;
import com.juanp.custommobs.mob.MobService;
import com.juanp.custommobs.mob.Texts;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Exclusividad de faccion de un objeto del catalogo.
 *
 * <p>Un objeto guardado con {@code /custommobs item faccion <nombre> <faccion>} queda
 * reservado: solo un jugador de esa faccion puede <b>recogerlo</b>, <b>equiparlo</b> o
 * <b>usarlo</b>. La marca viaja dentro del propio objeto (su NBT), asi que sobrevive a que
 * lo suelten, lo guarden en un cofre o lo reparta otro plugin.
 *
 * <p>No es una caja fuerte: el objeto sigue siendo un objeto normal. Lo que se impide es
 * que alguien de otra faccion lo use; no se le quita de las manos a quien ya lo tenga.
 */
public final class ItemFactionLock implements Listener {

    private final MobService service;
    private final ItemCatalog catalog;

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
        String own = this.service.factionOf(player);
        return own != null && own.equalsIgnoreCase(faction);
    }

    private void deny(Player player, String faction) {
        player.sendMessage(Texts.color("&cEse objeto es de la faccion &f" + faction
                + "&c: no es la tuya."));
    }

    /** No puede recogerlo del suelo. */
    @EventHandler(ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
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
        if (event.getHand() != EquipmentSlot.HAND) {
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

    /** No puede ponerselo en una ranura de armadura desde el inventario. */
    @EventHandler(ignoreCancelled = true)
    public void onEquip(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }
        ItemStack moving = event.getCursor();
        if (moving == null || moving.getType().isAir()) {
            moving = event.getCurrentItem();
        }
        String faction = this.factionOf(moving);
        if (faction == null || this.mayUse(player, faction)) {
            return;
        }
        boolean armorSlot = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof Player
                && event.getSlotType() == InventoryType.SlotType.ARMOR;
        if (armorSlot) {
            event.setCancelled(true);
            this.deny(player, faction);
        }
    }
}
