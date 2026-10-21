package com.juanp.custommobs.item;

import com.juanp.custommobs.CustomMobsPlugin;
import org.bukkit.Material;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.player.PlayerPickupArrowEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.persistence.PersistentDataType;

/**
 * Evita que una flecha normal recogida se mezcle con un stack de flechas custom.
 *
 * <p>Para el servidor no son el mismo item, aunque compartan material. Si el jugador
 * tiene flechas custom, la normal se coloca en un stack normal o en un slot libre. Si no
 * hay hueco, la recogida se cancela en silencio y la flecha queda en el suelo.
 */
public final class ArrowPickupListener implements Listener {

    private final CustomMobsPlugin plugin;

    public ArrowPickupListener(CustomMobsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) {
            return;
        }
        Item entity = event.getItem();
        ItemStack picked = entity.getItemStack();
        if (!isArrow(picked) || this.catalogName(picked) != null) {
            return;   // una custom se recoge como siempre
        }
        if (!this.hasCustomArrow(player)) {
            return;   // sin customs, el comportamiento vanilla es correcto
        }
        this.plugin.getLogger().info("[ArrowPickup] item=" + picked.getType()
                + " player=" + player.getName() + " catalog=" + this.catalogName(picked)
                + " customs=true");
        event.setCancelled(true);
        ItemStack rest = this.place(player, picked);
        if (rest.getAmount() <= 0) {
            entity.remove();
        } else {
            entity.setItemStack(rest);
        }
    }

    /** Las flechas clavadas o en el suelo usan este evento, no el de items. */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPickupArrow(PlayerPickupArrowEvent event) {
        Player player = event.getPlayer();
        AbstractArrow arrow = event.getArrow();
        ItemStack picked = arrow.getItemStack();
        if (!isArrow(picked) || this.catalogName(picked) != null) {
            return;
        }
        if (!this.hasCustomArrow(player)) {
            return;
        }
        this.plugin.getLogger().info("[ArrowPickup] arrow item=" + picked.getType()
                + " player=" + player.getName() + " catalog=" + this.catalogName(picked)
                + " customs=true");
        event.setCancelled(true);
        ItemStack rest = this.place(player, picked);
        if (rest.getAmount() <= 0) {
            arrow.remove();
        } else {
            arrow.setItemStack(rest);
        }
    }

    private ItemStack place(Player player, ItemStack picked) {
        PlayerInventory inventory = player.getInventory();
        ItemStack rest = picked.clone();
        // Solo slots libres: asi una normal no se fusiona con ningun stack, ni custom
        // ni normal. Es la unica forma segura si la marca del catalogo falta.
        // 1) slots libres.
        for (int slot = 0; slot < 36 && rest.getAmount() > 0; slot++) {
            ItemStack current = inventory.getItem(slot);
            if (current != null && !current.getType().isAir()) {
                continue;
            }
            int move = Math.min(rest.getMaxStackSize(), rest.getAmount());
            ItemStack placed = rest.clone();
            placed.setAmount(move);
            inventory.setItem(slot, placed);
            rest.setAmount(rest.getAmount() - move);
        }
        // Si queda algo, el llamador lo deja en el suelo o en la flecha, sin mensaje.
        return rest;
    }

    private boolean hasCustomArrow(Player player) {
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && isArrow(stack) && this.catalogName(stack) != null) {
                return true;
            }
        }
        // Si hay una flecha cargada, el jugador esta usando customs aunque la marca
        // del stack se haya perdido.
        return new com.juanp.custommobs.skill.ArrowSelection(this.plugin)
                .selected(player).isPresent();
    }

    private String catalogName(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return null;
        }
        return item.getItemMeta().getPersistentDataContainer()
                .get(this.plugin.drops().catalog().nameKey(), PersistentDataType.STRING);
    }

    private static boolean isArrow(ItemStack item) {
        if (item == null) {
            return false;
        }
        return item.getType() == Material.ARROW
                || item.getType() == Material.SPECTRAL_ARROW
                || item.getType() == Material.TIPPED_ARROW;
    }
}
