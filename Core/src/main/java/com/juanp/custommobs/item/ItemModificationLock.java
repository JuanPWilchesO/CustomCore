package com.juanp.custommobs.item;

import com.juanp.custommobs.drop.ItemCatalog;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.inventory.PrepareGrindstoneEvent;
import org.bukkit.event.inventory.PrepareSmithingEvent;
import org.bukkit.event.player.PlayerItemMendEvent;
import org.bukkit.inventory.AnvilInventory;
import org.bukkit.inventory.GrindstoneInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.SmithingInventory;

/**
 * Impide modificar un objeto del catalogo cuando su yml lo prohibe.
 *
 * <p>Claves: {@code modificable} (todo), {@code encantar-libros}, {@code encantar-mesa},
 * {@code renombrar}, {@code reparar}, {@code desencantar} y {@code tallar}. La marca va
 * dentro del objeto (NBT), asi que las copias repartidas conservan la proteccion.
 */
public final class ItemModificationLock implements Listener {

    private final ItemCatalog catalog;

    public ItemModificationLock(ItemCatalog catalog) {
        this.catalog = catalog;
    }

    private boolean denied(ItemStack item, String key) {
        return this.catalog.modificationDenied(item, key);
    }

    private String displayName(ItemStack item) {
        if (item == null || !item.hasItemMeta() || !item.getItemMeta().hasDisplayName()) {
            return item == null ? "" : item.getType().name();
        }
        return ChatColor.stripColor(item.getItemMeta().getDisplayName());
    }

    /** Yunque: renombrar, reparar o combinar con libro. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onAnvil(PrepareAnvilEvent event) {
        AnvilInventory inventory = event.getInventory();
        ItemStack left = inventory.getItem(0);
        ItemStack right = inventory.getItem(1);
        if (left == null || left.getType().isAir()) {
            return;
        }
        if (denied(left, "modificable") || denied(right, "modificable")) {
            event.setResult(null);
            return;
        }
        boolean book = right != null && right.getType() == Material.ENCHANTED_BOOK;
        boolean repair = right != null && !book;
        String renameText = inventory.getRenameText();
        boolean rename = renameText != null && !renameText.isBlank()
                && !renameText.equals(this.displayName(left));
        if (rename && denied(left, "renombrar")) {
            event.setResult(null);
            return;
        }
        if (book && (denied(left, "encantar-libros") || denied(right, "encantar-libros"))) {
            event.setResult(null);
            return;
        }
        if (repair && denied(left, "reparar")) {
            event.setResult(null);
        }
    }

    /** Mesa de encantamientos. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onEnchant(EnchantItemEvent event) {
        if (denied(event.getItem(), "encantar-mesa")) {
            event.setCancelled(true);
        }
    }

    /** Grindstone: quitar encantamientos. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGrindstone(PrepareGrindstoneEvent event) {
        GrindstoneInventory inventory = event.getInventory();
        for (int slot = 0; slot <= 1; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (denied(item, "modificable") || denied(item, "desencantar")) {
                event.setResult(null);
                return;
            }
        }
    }

    /** Mending repara el objeto con XP; cuenta como 'reparar'. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMend(PlayerItemMendEvent event) {
        if (denied(event.getItem(), "reparar")) {
            event.setCancelled(true);
        }
    }

    /** Mesa de herrería: transformar/mejorar. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onSmithing(PrepareSmithingEvent event) {
        SmithingInventory inventory = event.getInventory();
        for (int slot = 0; slot <= 2; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (denied(item, "modificable") || denied(item, "tallar")) {
                event.setResult(null);
                return;
            }
        }
    }
}
