package com.juanp.custommobs.skill;

import com.juanp.custommobs.CustomMobsPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Optional;

/**
 * Flecha cargada por jugador.
 *
 * <p>Se guarda en el PDC del jugador, no en el arco: asi sirve con cualquier arco que
 * tome y sobrevive a reconexiones y reinicios. El arco solo presta la animacion; la
 * municion y sus skills salen de aqui.
 */
public final class ArrowSelection {

    private final CustomMobsPlugin plugin;
    private final NamespacedKey key;

    public ArrowSelection(CustomMobsPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "flecha_cargada");
    }

    /** Nombre de catalogo de la flecha cargada, si hay. */
    public Optional<String> selected(Player player) {
        String name = player.getPersistentDataContainer().get(this.key, PersistentDataType.STRING);
        return name == null || name.isBlank() ? Optional.empty() : Optional.of(name);
    }

    public void load(Player player, String name) {
        player.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, name);
    }

    public void clear(Player player) {
        player.getPersistentDataContainer().remove(this.key);
    }

    /** Cuantas flechas de ese tipo lleva encima (incluye offhand). */
    public int count(Player player, String name) {
        ItemStack template = this.template(name);
        if (template == null) {
            return 0;
        }
        int total = 0;
        for (ItemStack stack : player.getInventory().getContents()) {
            if (stack != null && stack.isSimilar(template)) {
                total += stack.getAmount();
            }
        }
        return total;
    }

    /** Gasta una unidad; {@code false} si no habia. */
    public boolean consume(Player player, String name) {
        ItemStack template = this.template(name);
        if (template == null) {
            return false;
        }
        ItemStack one = template.clone();
        one.setAmount(1);
        return player.getInventory().removeItem(one).isEmpty();
    }

    /** Nombre visible de la flecha, para los avisos. */
    public String displayName(String name) {
        ItemStack template = this.template(name);
        if (template != null && template.hasItemMeta()
                && template.getItemMeta().hasDisplayName()) {
            return org.bukkit.ChatColor.stripColor(template.getItemMeta().getDisplayName());
        }
        return name;
    }

    private ItemStack template(String name) {
        return name == null ? null : this.plugin.drops().catalog().get(name).orElse(null);
    }
}
